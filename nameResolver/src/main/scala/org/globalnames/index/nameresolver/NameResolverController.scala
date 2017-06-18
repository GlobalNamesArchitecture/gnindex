package org.globalnames
package index
package nameresolver

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.nameresolver.{Response, Service => NameResolverService}
import thrift.nameresolver.Service.NameResolve

@Singleton
class NameResolverController @Inject()(resolver: NameResolverFactory)
  extends Controller
     with NameResolverService.BaseServiceIface {

  override val nameResolve: ThriftMethodService[NameResolve.Args, Seq[Response]] =
    handle(NameResolve) { args: NameResolve.Args =>
      info(s"Responding to nameResolve")
      resolver.resolveExact(args.request)
    }
}