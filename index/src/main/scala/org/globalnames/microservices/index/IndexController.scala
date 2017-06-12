package org.globalnames
package microservices.index

import javax.annotation.Nullable
import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import com.twitter.util.Future
import nameresolve.thriftscala.{Name, Response, Result}
import thriftscala.IndexService
import thriftscala.IndexService.NameResolve
import resolver.Resolver

@Singleton
class IndexController @Inject()(@Nullable resolver: Resolver)
  extends Controller
     with IndexService.BaseServiceIface {

  override val nameResolve: ThriftMethodService[NameResolve.Args, Seq[Response]] =
    handle(NameResolve) { args: NameResolve.Args =>
      info(s"Responding to nameResolve")

      Future.value { args.request.map { req =>
        Response(
          total = args.request.size,
          results = req.names.map { nr =>
            Result(name = Name(uuid = "", value = nr.value), canonicalName = None)
          }
        )
      }}
    }
}
