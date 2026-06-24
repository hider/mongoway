package io.github.hider.mongoway.commands


const val COMMAND_GROUP = "MongoWay"
const val CS_DESCRIPTION_SHORT = "fully qualified MongoDB connection string which includes the database name"
const val CS_DESCRIPTION_FULL = """  [1mconnectionString[0m $CS_DESCRIPTION_SHORT
                   Example: mongodb://my.mongo.host:27017/my_database?myOptionKey=myOptionValue"""
const val PATHS_DESCRIPTION_SHORT = "relative or absolute path of the change log file"
const val PATHS_DESCRIPTION_FULL = """  [1mchangelogPath[0m $PATHS_DESCRIPTION_SHORT
                Pass a single '-' to read change log from the standard input."""
const val UNIQUE_ID_DESCRIPTION = "the unique identifier of the change set"
