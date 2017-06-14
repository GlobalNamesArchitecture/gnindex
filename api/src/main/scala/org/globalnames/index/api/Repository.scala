package org.globalnames
package index
package api

import javax.inject.Inject

import nameresolver.thriftscala.Service

class Repository @Inject() (nameResolverClient: Service.FutureIface) {

}
