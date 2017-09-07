package org.globalnames
package index
package namefilter

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.ResultScored
import thrift.namefilter.{Service => NameFilterService, NameStringUuidDataSources}
import thrift.namefilter.Service.{NameString, NameStringDataSources}

@Singleton
class NameFilterController @Inject()(nameFilter: NameFilter, nameDataSources: NameDataSources)
  extends Controller
     with NameFilterService.BaseServiceIface {

  override val nameString: ThriftMethodService[NameString.Args, Seq[ResultScored]] =
    handle(NameString) { args: NameString.Args =>
      info(s"Responding to nameFilter")
      nameFilter.resolveString(args.request)
    }

  override val nameStringDataSources: ThriftMethodService[NameStringDataSources.Args,
                                                          Seq[NameStringUuidDataSources]] =
    handle(NameStringDataSources) { args: NameStringDataSources.Args =>
      info(s"Responding nameStringDataSources")
      nameDataSources.resolve(args.nameUuids)
    }
}
