package com.rockthejvm.reviewboard.config

final case class StripeConfig(
    key: String,
    secret: String, // webhook secret
    price: String,  // price id in Stripe
    successUrl: String,
    cancelUrl: String
)
