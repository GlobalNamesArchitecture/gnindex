package org.globalnames
package microservices
package api

import javax.inject.Inject

import nameresolver.thriftscala.IndexService

class Repository @Inject() (indexClient: IndexService.FutureIface) {

}
