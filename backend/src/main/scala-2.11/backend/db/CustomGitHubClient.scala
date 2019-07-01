package backend.common

import java.net.HttpURLConnection
import java.time.Instant

import org.eclipse.egit.github.core.client.GitHubClient

class CustomGitHubClient extends GitHubClient {
  var resetDate: Instant = null

  override def updateRateLimits(request: HttpURLConnection) = {
    val reset = request.getHeaderField("X-RateLimit-Reset")
    if (reset != null && reset.length() > 0) {
      try {
        resetDate = Instant.ofEpochSecond(reset.toLong)
      } catch {
        case _: Throwable =>
      }
    }
    super.updateRateLimits(request)
  }
}
