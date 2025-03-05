package com.rockthejvm.reviewboard.config

import sttp.model.Uri

final case class BackendClientConfig(uri: Option[Uri])
