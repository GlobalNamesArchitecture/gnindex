package org.globalnames
package index
package nameresolver

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.{nameresolver => nr, matcher => mtch}
import slick.jdbc.PostgresProfile.api._

@Singleton
class NameResolverController @Inject()(implicit database: Database,
                                                matcherClient: mtch.Service.FutureIface)
  extends Controller with nr.Service.BaseServiceIface {

  override val nameResolve: ThriftMethodService[nr.Service.NameResolve.Args, nr.Responses] =
    handle(nr.Service.NameResolve) { args: nr.Service.NameResolve.Args =>
      info(s"Responding to nameResolve")
      new NameResolver(args.request, database, matcherClient).resolveExact()
    }
}
