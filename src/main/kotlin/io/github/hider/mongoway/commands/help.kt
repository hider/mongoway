package io.github.hider.mongoway.commands


const val COMMAND_GROUP = "MongoWay"
const val CS_DESCRIPTION = """  [1mconnectionString[0m fully qualified MongoDB connection string which includes the database name
                   Example: mongodb://my.mongo.host:27017/my_database?myOptionKey=myOptionValue"""
const val PATHS_DESCRIPTION = """  [1mchangelogPath[0m relative or absolute path of the change log file
                Pass a single '-' to read change log from the standard input."""
