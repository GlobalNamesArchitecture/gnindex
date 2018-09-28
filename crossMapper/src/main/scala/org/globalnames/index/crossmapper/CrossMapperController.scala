package org.globalnames
package index
package crossmapper

import javax.inject.{Inject, Singleton}
import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.{crossmapper => cm}

@Singleton
class CrossMapperController @Inject()(crossMapper: CrossMapper)
  extends Controller
     with cm.Service.ServicePerEndpoint {

  override val resolve: ThriftMethodService[cm.Service.Resolve.Args,
                                            Seq[cm.Result]] = {
    handle(cm.Service.Resolve) { args: cm.Service.Resolve.Args =>
      val result = crossMapper.resolve(
        databaseSourceId = args.dbSourceId,
        databaseTargetId = args.dbTargetId,
        suppliedIds = args.localIds
      )
      result
    }
  }

}
