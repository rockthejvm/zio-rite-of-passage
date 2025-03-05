package com.rockthejvm.reviewboard.services

import zio.*
import com.stripe.Stripe as TheStripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import scala.jdk.OptionConverters.*

import com.rockthejvm.reviewboard.config.StripeConfig
import com.rockthejvm.reviewboard.config.Configs
import com.stripe.net.Webhook

trait PaymentService {
  // create a session
  def createCheckoutSession(invitePackId: Long, userName: String): Task[Option[Session]]
  // handle a webhook event
  def handleWebhookEvent[A](
      signature: String,
      payload: String,
      action: String => Task[A]
  ): Task[Option[A]]
}

class PaymentServiceLive private (config: StripeConfig) extends PaymentService {
  def createCheckoutSession(invitePackId: Long, userName: String): Task[Option[Session]] =
    ZIO
      .attempt {
        SessionCreateParams
          .builder()
          .setMode(SessionCreateParams.Mode.PAYMENT)
          .setSuccessUrl(config.successUrl)
          .setCancelUrl(config.cancelUrl)
          .setCustomerEmail(userName)
          .setClientReferenceId(invitePackId.toString)
          // ^ my own payload - will be used on the webhook
          .setInvoiceCreation(
            SessionCreateParams.InvoiceCreation
              .builder()
              .setEnabled(true)
              .build()
          )
          .setPaymentIntentData(
            SessionCreateParams.PaymentIntentData
              .builder()
              .setReceiptEmail(userName)
              .build()
          )
          // need to add a product
          .addLineItem(
            SessionCreateParams.LineItem
              .builder()
              .setPrice(config.price) // unique id of your Stripe product
              .setQuantity(1L)
              .build()
          )
          .build()
      }
      .map(params => Session.create(params))
      .map(Option(_))
      .logError("Stripe session creation FAILED")
      .catchSome { case _ =>
        ZIO.none
      }

  override def handleWebhookEvent[A](
      signature: String,
      payload: String,
      action: String => Task[A]
  ): Task[Option[A]] =
    ZIO
      .attempt {
        Webhook.constructEvent(payload, signature, config.secret)
      }
      .flatMap { event =>
        event.getType() match {
          case "checkout.session.completed" =>
            ZIO.foreach(
              event
                .getDataObjectDeserializer()
                .getObject()
                .toScala
                .map(_.asInstanceOf[Session])
                .map(_.getClientReferenceId())
            )(action)
          case _ =>
            ZIO.none // discard the event
        }
      }
    /*
        built webhook event
        check event type
          if the event type is success
            parse the event
            handle the activation of the pack
     */
}

object PaymentServiceLive {
  val layer = ZLayer {
    for {
      config <- ZIO.service[StripeConfig]
      _      <- ZIO.attempt(TheStripe.apiKey = config.key)
    } yield new PaymentServiceLive(config)
  }

  val configuredLayer =
    Configs.makeLayer[StripeConfig]("rockthejvm.stripe") >>> layer
}
