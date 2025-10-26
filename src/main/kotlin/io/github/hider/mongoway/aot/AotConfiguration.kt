package io.github.hider.mongoway.aot

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

@Configuration
@ImportRuntimeHints(AotRuntimeHints::class)
class AotConfiguration
