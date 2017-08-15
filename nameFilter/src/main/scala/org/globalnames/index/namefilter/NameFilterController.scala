package org.globalnames
package index
package namefilter

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.ResultScored
import thrift.namefilter.{Service => NameFilterService}
import thrift.namefilter.Service.NameString

@Singleton
class NameFilterController @Inject()(nameFilter: NameFilter)
  extends Controller
     with NameFilterService.BaseServiceIface {

  override val nameString: ThriftMethodService[NameString.Args, Seq[ResultScored]] =
    handle(NameString) { args: NameString.Args =>
      info(s"Responding to nameFilter")
      nameFilter.resolveString(args.request)
    }
}
