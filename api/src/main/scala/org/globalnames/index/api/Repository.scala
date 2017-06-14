package org.globalnames
package index
package api

import javax.inject.Inject

import thrift.nameresolver.{Service => NameResolverService}

class Repository @Inject() (nameResolverClient: NameResolverService.FutureIface) {

}
