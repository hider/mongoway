# MongoWay

**A Database Change Management Tool for MongoDB**

Inspired by [Liquibase](https://github.com/liquibase/liquibase),
MongoWay is a command-line tool designed to help developers manage changes in MongoDB databases.

[![Gradle CI](https://github.com/hider/mongoway/actions/workflows/gradle-ci.yml/badge.svg)](https://github.com/hider/mongoway/actions/workflows/gradle-ci.yml)
[![codecov](https://codecov.io/github/hider/mongoway/graph/badge.svg?token=ZZ7L88LGKO)](https://codecov.io/github/hider/mongoway)
[![Coverage Status](https://coveralls.io/repos/github/hider/mongoway/badge.svg?branch=main)](https://coveralls.io/github/hider/mongoway?branch=main)

## Features

- Specify the changes in JSON files, [Extended JSON](https://www.mongodb.com/docs/manual/reference/mongodb-extended-json/) syntax is supported.
- Executed changes are tracked in a dedicated change log collection to ensure changes executed once (ideal for your CI pipeline!).
- Rollback changes are generated for trivial changes (you can also provide your own).
- Validate changes before applying them.

## Example

A change log file is a JSON file with an array of objects, each object representing a change set.

```json
[
    {
        "globalUniqueChangeId": "TICKET-12340",
        "author": "alice",
        "targetCollection": "app_config",
        "change": {
            "action": "insertOne",
            "document": {
                "context": "template",
                "version": "5.1.2",
                "content": {
                    "title": "Demo"
                }
            }
        }
    },
    {
        "globalUniqueChangeId": "TICKET-12341",
        "author": "bob",
        "targetCollection": "app_config",
        "change": {
            "action": "updateOne",
            "filter": {
                "context": "version"
            },
            "document": {
                "$inc": {
                    "value": 1
                }
            }
        },
        "description": "Increment the version number"
    }
]
```

The above change log file contains two change sets.
The first one inserts a document into the `app_config` collection, and the second one updates a document in the same collection.
There are mandatory properties like `globalUniqueChangeId`, `author`, `targetCollection` and `change` and optional like the `description` property.

The `globalUniqueChangeId` is a unique identifier for the change set, used to track the change set across different executions.
Duplicate change sets with the same `globalUniqueChangeId` will be result in an error.

Let's execute the change log file using the MongoWay command line tool:

```bash
mongoway update mongodb://dev.mongodb.example.com:27017/dev_app path/to/changelog.json
```

The _update_ command will apply the changes in the change log file to the specified MongoDB database.
The first argument is the connection string which should include the database name.


## Supported change actions

| Name          | Description                     | Auto rollback   |
|---------------|---------------------------------|-----------------|
| `insertOne`   | Insert a single document        | ☑️ Supported    |
| `insertMany`  | Insert multiple documents       | ☑️ Supported    |
| `updateOne`   | Update a single document        | ☑️ Supported    |
| `updateMany`  | Update multiple documents       | ❌ Not supported |
| `deleteOne`   | Delete a single document        | ☑️ Supported    |
| `deleteMany`  | Delete a multiple documents     | ❌ Not supported |
| `createIndex` | Create an index on a collection | ☑️ Supported    |
| `dropIndex`   | Drop an index from a collection | ☑️ Supported    |

Actions with _auto rollback_ generate rollback change if custom rollback change is not specified.

## Usage

Ensure Java 24 or newer runtime is available on your system.
Download the latest release archive from the [releases page](https://github.com/hider/mongoway/releases) and extract its contents to a desired directory.

Navigate to the extracted directory and launch MongoWay by executing `bin/mongoway`.

### Supported commands

- `update <connection string> <path to change log>...`: Apply changes from the specified change log(s) to the MongoDB database.
- `rollback <connection string> <globalUniqueChangeId>`: Rollback the change with the specified `globalUniqueChangeId`.
- `validate <path to change log>...`: Validate the change log(s) for correctness and consistency.
- `query <connection string> <globalUniqueChangeId>`: Find a change set with the specified `globalUniqueChangeId`.

#### connection string

Refer to [MongoDB Docs](https://www.mongodb.com/docs/manual/reference/connection-string/#standard-connection-string-format) for the connection string format
and ensure it includes the default database name.
For local databases listening on the default port 27017, it is enough to specify the database name only.

#### path to change log

The path to the change log(s) can be either absolute or relative.
For Windows paths, use forward slashes (`/`) or double backslashes (`\\`) as directory separators instead of the default single backslashes (`\`).

### Container images

MongoWay images are available in three variants:

- `ghcr.io/hider/mongoway:<version>-alpaquita`: Alpaquita Linux with JRE and jar files.
- `ghcr.io/hider/mongoway:<version>-alpine`:  Alpine Linux with JRE and jar files.
- `ghcr.io/hider/mongoway:<version>-native`: Native image built with Paketo Buildpacks.

## License

This project is licensed under _GNU General Public License v3.0_, see [LICENSE](/LICENSE).
