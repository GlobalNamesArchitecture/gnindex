package org.globalnames
package index
package namefilter

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.Result
import thrift.namefilter.{Response, Service => NameFilterService}
import thrift.namefilter.Service.{NameString, NameStringByUuid}

@Singleton
class NameFilterController @Inject()(nameFilter: NameFilter, nameStrindByUUID: NameStringByUUID)
  extends Controller
     with NameFilterService.BaseServiceIface {

  override val nameString: ThriftMethodService[NameString.Args, Seq[Result]] =
    handle(NameString) { args: NameString.Args =>
      info(s"Responding to nameFilter")
      nameFilter.resolveString(args.request)
    }

  override val nameStringByUuid: ThriftMethodService[NameStringByUuid.Args, Seq[Response]] =
    handle(NameStringByUuid) { args: NameStringByUuid.Args =>
      info(s"Responding nameStringDataSources")
      nameStrindByUUID.resolve(args.nameUuids)
    }
}
