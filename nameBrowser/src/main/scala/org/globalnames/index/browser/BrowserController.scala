package org.globalnames
package index
package browser

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import com.twitter.util.Future
import thrift.{namebrowser => nb}

@Singleton
class BrowserController @Inject()(browser: Browser)
  extends Controller
     with nb.Service.ServicePerEndpoint {

  override val tripletsStartingWith: ThriftMethodService[nb.Service.TripletsStartingWith.Args,
                                                         Seq[nb.Triplet]] =
    handle(nb.Service.TripletsStartingWith) { args: nb.Service.TripletsStartingWith.Args =>
      val letterChar = args.letter.toChar
      info(s"Responding to tripletsStartingWith: $letterChar")
      Future.value(browser.tripletsStartingWith(letterChar))
    }
}
