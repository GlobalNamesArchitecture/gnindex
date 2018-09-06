package org.globalnames
package index
package namefilter

import javax.inject.{Inject, Singleton}

import com.twitter.finatra.thrift.Controller
import com.twitter.finatra.thrift.internal.ThriftMethodService
import thrift.DataSource
import thrift.{namefilter => nf}

@Singleton
class NameFilterController @Inject()(nameFilter: NameFilter,
                                     nameStrindByUUID: NameStringByUUID,
                                     dataSourceByIdResolver: DataSourceByIdResolver)
  extends Controller with nf.Service.ServicePerEndpoint {

  override val nameString: ThriftMethodService[nf.Service.NameString.Args,
                                               nf.ResponseNameStrings] =
    handle(nf.Service.NameString) { args: nf.Service.NameString.Args =>
      info(s"Responding to nameFilter")
      nameFilter.resolveString(args.request)
    }

  override val nameStringByUuid: ThriftMethodService[nf.Service.NameStringByUuid.Args,
                                                     Seq[nf.Response]] =
    handle(nf.Service.NameStringByUuid) { args: nf.Service.NameStringByUuid.Args =>
      info(s"Responding nameStringDataSources")
      nameStrindByUUID.resolve(args.nameUuids)
    }

  override val dataSourceById: ThriftMethodService[nf.Service.DataSourceById.Args,
                                                   Seq[DataSource]] =
    handle(nf.Service.DataSourceById) { args: nf.Service.DataSourceById.Args =>
      info(s"Responding dataSourceById")
      dataSourceByIdResolver.resolve(args.dataSourceIds)
    }
}
