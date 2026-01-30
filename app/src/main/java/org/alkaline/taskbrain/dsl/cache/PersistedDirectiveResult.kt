package org.alkaline.taskbrain.dsl.cache

import com.google.firebase.Timestamp
import org.alkaline.taskbrain.dsl.runtime.DslValue

/**
 * Firestore-serializable version of CachedDirectiveResult.
 *
 * Phase 6: Firestore persistence layer.
 *
 * Stored at:
 * - Global cache: `directiveCache/{directiveHash}`
 * - Per-note cache: `notes/{noteId}/directiveResults/{directiveHash}`
 */
data class PersistedDirectiveResult(
    /** Serialized DslValue result (null if error) */
    val result: Map<String, Any?>? = null,

    /** Serialized error (null if success) */
    val error: Map<String, Any?>? = null,

    /** Serialized dependencies */
    val dependencies: Map<String, Any?> = emptyMap(),

    /** Collection-level metadata hashes at cache time */
    val metadataHashes: Map<String, String?> = emptyMap(),

    /** Per-note content hashes: noteId -> { firstLineHash, nonFirstLineHash } */
    val noteContentHashes: Map<String, Map<String, String?>> = emptyMap(),

    /** When this result was cached */
    val cachedAt: Timestamp? = null
) {
    /** No-arg constructor for Firestore deserialization */
    constructor() : this(null, null, emptyMap(), emptyMap(), emptyMap(), null)

    companion object {
        /**
         * Convert a CachedDirectiveResult to PersistedDirectiveResult for Firestore storage.
         */
        fun fromCachedResult(cached: CachedDirectiveResult): PersistedDirectiveResult {
            return PersistedDirectiveResult(
                result = cached.result?.serialize(),
                error = cached.error?.let { DirectiveErrorSerializer.serialize(it) },
                dependencies = serializeDependencies(cached.dependencies),
                metadataHashes = serializeMetadataHashes(cached.metadataHashes),
                noteContentHashes = serializeContentHashes(cached.noteContentHashes),
                cachedAt = Timestamp(cached.cachedAt / 1000, ((cached.cachedAt % 1000) * 1_000_000).toInt())
            )
        }

        /**
         * Convert a PersistedDirectiveResult back to CachedDirectiveResult.
         */
        fun toCachedResult(persisted: PersistedDirectiveResult): CachedDirectiveResult {
            val result = persisted.result?.let { DslValue.deserialize(it) }
            val error = persisted.error?.let { DirectiveErrorSerializer.deserialize(it) }
            val dependencies = deserializeDependencies(persisted.dependencies)
            val metadataHashes = deserializeMetadataHashes(persisted.metadataHashes)
            val noteContentHashes = deserializeContentHashes(persisted.noteContentHashes)
            val cachedAt = persisted.cachedAt?.let { it.seconds * 1000 + it.nanoseconds / 1_000_000 }
                ?: System.currentTimeMillis()

            return if (result != null) {
                CachedDirectiveResult(
                    result = result,
                    error = null,
                    dependencies = dependencies,
                    noteContentHashes = noteContentHashes,
                    metadataHashes = metadataHashes,
                    cachedAt = cachedAt
                )
            } else if (error != null) {
                CachedDirectiveResult(
                    result = null,
                    error = error,
                    dependencies = dependencies,
                    noteContentHashes = noteContentHashes,
                    metadataHashes = metadataHashes,
                    cachedAt = cachedAt
                )
            } else {
                throw IllegalStateException("PersistedDirectiveResult must have either result or error")
            }
        }

        private fun serializeDependencies(deps: DirectiveDependencies): Map<String, Any?> {
            return mapOf(
                "firstLineNotes" to deps.firstLineNotes.toList(),
                "nonFirstLineNotes" to deps.nonFirstLineNotes.toList(),
                "dependsOnPath" to deps.dependsOnPath,
                "dependsOnModified" to deps.dependsOnModified,
                "dependsOnCreated" to deps.dependsOnCreated,
                "dependsOnViewed" to deps.dependsOnViewed,
                "dependsOnNoteExistence" to deps.dependsOnNoteExistence,
                "usesSelfAccess" to deps.usesSelfAccess,
                "hierarchyDeps" to deps.hierarchyDeps.map { serializeHierarchyDep(it) }
            )
        }

        private fun serializeHierarchyDep(dep: HierarchyDependency): Map<String, Any?> {
            return mapOf(
                "path" to serializeHierarchyPath(dep.path),
                "resolvedNoteId" to dep.resolvedNoteId,
                "field" to dep.field?.name,
                "fieldHash" to dep.fieldHash
            )
        }

        private fun serializeHierarchyPath(path: HierarchyPath): Map<String, Any?> {
            return when (path) {
                is HierarchyPath.Up -> mapOf("type" to "up")
                is HierarchyPath.UpN -> mapOf("type" to "upN", "levels" to path.levels)
                is HierarchyPath.Root -> mapOf("type" to "root")
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun deserializeDependencies(map: Map<String, Any?>): DirectiveDependencies {
            return DirectiveDependencies(
                firstLineNotes = (map["firstLineNotes"] as? List<String>)?.toSet() ?: emptySet(),
                nonFirstLineNotes = (map["nonFirstLineNotes"] as? List<String>)?.toSet() ?: emptySet(),
                dependsOnPath = map["dependsOnPath"] as? Boolean ?: false,
                dependsOnModified = map["dependsOnModified"] as? Boolean ?: false,
                dependsOnCreated = map["dependsOnCreated"] as? Boolean ?: false,
                dependsOnViewed = map["dependsOnViewed"] as? Boolean ?: false,
                dependsOnNoteExistence = map["dependsOnNoteExistence"] as? Boolean ?: false,
                usesSelfAccess = map["usesSelfAccess"] as? Boolean ?: false,
                hierarchyDeps = (map["hierarchyDeps"] as? List<Map<String, Any?>>)
                    ?.map { deserializeHierarchyDep(it) } ?: emptyList()
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun deserializeHierarchyDep(map: Map<String, Any?>): HierarchyDependency {
            val pathMap = map["path"] as? Map<String, Any?> ?: emptyMap()
            val path = deserializeHierarchyPath(pathMap)
            val fieldName = map["field"] as? String
            val field = fieldName?.let { NoteField.valueOf(it) }

            return HierarchyDependency(
                path = path,
                resolvedNoteId = map["resolvedNoteId"] as? String,
                field = field,
                fieldHash = map["fieldHash"] as? String
            )
        }

        private fun deserializeHierarchyPath(map: Map<String, Any?>): HierarchyPath {
            return when (map["type"]) {
                "up" -> HierarchyPath.Up
                "upN" -> HierarchyPath.UpN((map["levels"] as? Number)?.toInt() ?: 1)
                "root" -> HierarchyPath.Root
                else -> HierarchyPath.Up  // Default
            }
        }

        private fun serializeMetadataHashes(hashes: MetadataHashes): Map<String, String?> {
            return mapOf(
                "pathHash" to hashes.pathHash,
                "modifiedHash" to hashes.modifiedHash,
                "createdHash" to hashes.createdHash,
                "viewedHash" to hashes.viewedHash,
                "existenceHash" to hashes.existenceHash
            )
        }

        private fun deserializeMetadataHashes(map: Map<String, String?>): MetadataHashes {
            return MetadataHashes(
                pathHash = map["pathHash"],
                modifiedHash = map["modifiedHash"],
                createdHash = map["createdHash"],
                viewedHash = map["viewedHash"],
                existenceHash = map["existenceHash"]
            )
        }

        private fun serializeContentHashes(hashes: Map<String, ContentHashes>): Map<String, Map<String, String?>> {
            return hashes.mapValues { (_, h) ->
                mapOf(
                    "firstLineHash" to h.firstLineHash,
                    "nonFirstLineHash" to h.nonFirstLineHash
                )
            }
        }

        private fun deserializeContentHashes(map: Map<String, Map<String, String?>>): Map<String, ContentHashes> {
            return map.mapValues { (_, h) ->
                ContentHashes(
                    firstLineHash = h["firstLineHash"],
                    nonFirstLineHash = h["nonFirstLineHash"]
                )
            }
        }
    }
}

/**
 * Serialization utilities for DirectiveError.
 */
object DirectiveErrorSerializer {
    fun serialize(error: DirectiveError): Map<String, Any?> {
        val type = when (error) {
            is SyntaxError -> "syntax"
            is TypeError -> "type"
            is ArgumentError -> "argument"
            is FieldAccessError -> "fieldAccess"
            is ValidationError -> "validation"
            is UnknownIdentifierError -> "unknownIdentifier"
            is CircularDependencyError -> "circularDependency"
            is ArithmeticError -> "arithmetic"
            is NetworkError -> "network"
            is TimeoutError -> "timeout"
            is ResourceUnavailableError -> "resourceUnavailable"
            is PermissionError -> "permission"
            is ExternalServiceError -> "externalService"
        }

        return mapOf(
            "type" to type,
            "message" to error.message,
            "position" to error.position
        )
    }

    fun deserialize(map: Map<String, Any?>): DirectiveError {
        val type = map["type"] as? String ?: throw IllegalArgumentException("Missing error type")
        val message = map["message"] as? String ?: "Unknown error"
        val position = (map["position"] as? Number)?.toInt()

        return when (type) {
            "syntax" -> SyntaxError(message, position)
            "type" -> TypeError(message, position)
            "argument" -> ArgumentError(message, position)
            "fieldAccess" -> FieldAccessError(message, position)
            "validation" -> ValidationError(message, position)
            "unknownIdentifier" -> UnknownIdentifierError(message, position)
            "circularDependency" -> CircularDependencyError(message, position)
            "arithmetic" -> ArithmeticError(message, position)
            "network" -> NetworkError(message, position)
            "timeout" -> TimeoutError(message, position)
            "resourceUnavailable" -> ResourceUnavailableError(message, position)
            "permission" -> PermissionError(message, position)
            "externalService" -> ExternalServiceError(message, position)
            else -> TypeError(message, position)  // Default to deterministic
        }
    }
}
