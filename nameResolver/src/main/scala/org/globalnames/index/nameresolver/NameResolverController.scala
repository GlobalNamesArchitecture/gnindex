package org.globalnames
package index
package nameresolver

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.nameresolver.{Name, Response, Result, Service => NameResolverService}
import thrift.nameresolver.Service.NameResolve

@Singleton
class NameResolverController @Inject()(resolver: NameResolver)
  extends Controller
     with NameResolverService.BaseServiceIface {

  override val nameResolve: ThriftMethodService[NameResolve.Args, Seq[Response]] =
    handle(NameResolve) { args: NameResolve.Args =>
      info(s"Responding to nameResolve")
      val arg = args.request.flatMap { _.names.map { _.value }}
      val responseF = resolver.resolveExact(arg).map { rs =>
        Response(
          total = rs.size,
          results = rs.map { r => Result(name = Name(uuid = "", value = r))}
        )
      }
      responseF.map { resp => Seq(resp) }
    }
}
