# MongoWay - A Database Change Management Tool for MongoDB

Inspired by [Liquibase](https://docs.liquibase.com/concepts/introduction-to-liquibase.html),
MongoWay is a command-line tool designed to help developers manage changes in MongoDB databases.

[![Gradle CI](https://github.com/hider/mongoway/actions/workflows/gradle-ci.yml/badge.svg)](https://github.com/hider/mongoway/actions/workflows/gradle-ci.yml)
[![codecov](https://codecov.io/github/hider/mongoway/graph/badge.svg?token=ZZ7L88LGKO)](https://codecov.io/github/hider/mongoway)

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

- `insertOne`: Insert a single document.
- `insertMany`: Insert multiple documents.
- `updateOne`: Update a single document.
- `updateMany`: Update multiple documents.
- `deleteOne`: Delete a single document.
- `createIndex`: Create an index on a collection.
- `dropIndex`: Drop an index from a collection.

## License

This project is licensed under _GNU General Public License v3.0_, see [LICENSE](/LICENSE).
