object Helper {
  def formatGraphQL(query: String): String = query.replace("\n", "").replace("\"", "\\\"")
}
