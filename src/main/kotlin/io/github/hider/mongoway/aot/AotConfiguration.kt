package io.github.hider.mongoway.aot

import io.github.hider.mongoway.*
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

@Configuration
@ImportRuntimeHints(AotRuntimeHints::class)
@RegisterReflectionForBinding(
    InsertOne::class,
    InsertMany::class,
    UpdateOne::class,
    UpdateMany::class,
    DeleteOne::class,
    CreateIndex::class,
    DropIndex::class,
)
class AotConfiguration
