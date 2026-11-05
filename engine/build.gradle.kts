import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.ow2.asm:asm-tree:9.7.1") }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

/**
 * JDA compiles two regexes with Pattern.UNICODE_CHARACTER_CLASS (flag 256) in
 * net.dv8tion.jda.internal.utils.Checks. Android does not support that flag and throws
 * "UNICODE_CHARACTER_CLASS flag not supported" -> the app crashed as soon as a bot started.
 * On Android `\w` is Unicode-aware anyway, so the flag is simply removed from the bytecode.
 */
abstract class PatchJdaForAndroid : DefaultTask() {
    @get:InputFiles abstract val jdaJar: ConfigurableFileCollection
    @get:OutputFile abstract val patchedJar: RegularFileProperty

    @TaskAction
    fun patch() {
        var patchedCalls = 0
        ZipFile(jdaJar.singleFile).use { zip ->
            ZipOutputStream(patchedJar.get().asFile.outputStream()).use { out ->
                for (entry in zip.entries()) {
                    val bytes = zip.getInputStream(entry).readBytes()
                    val data = if (entry.name.endsWith(".class") && !entry.name.startsWith("META-INF/")) {
                        val node = ClassNode().also { ClassReader(bytes).accept(it, 0) }
                        var changed = false
                        for (method in node.methods) for (insn in method.instructions.toArray()) {
                            if (insn is MethodInsnNode && insn.owner == "java/util/regex/Pattern" && insn.name == "compile" &&
                                insn.desc == "(Ljava/lang/String;I)Ljava/util/regex/Pattern;"
                            ) {
                                val flags = insn.previous
                                if (flags is IntInsnNode && (flags.operand and 256) != 0) {
                                    flags.operand = flags.operand and 256.inv()
                                    changed = true
                                    patchedCalls++
                                }
                            }
                        }
                        if (changed) ClassWriter(0).also { node.accept(it) }.toByteArray() else bytes
                    } else {
                        bytes
                    }
                    // Drop signatures; the jar content changed.
                    if (entry.name.startsWith("META-INF/") && (entry.name.endsWith(".SF") || entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA"))) continue
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(data)
                    out.closeEntry()
                }
            }
        }
        check(patchedCalls == 2) { "Expected to patch 2 regex flags in JDA, patched $patchedCalls. Check the JDA version." }
        logger.lifecycle("Patched $patchedCalls UNICODE_CHARACTER_CLASS regex flags in JDA for Android")
    }
}

val jdaOriginal: Configuration by configurations.creating { isTransitive = false }

val patchJda = tasks.register<PatchJdaForAndroid>("patchJdaForAndroid") {
    jdaJar.from(jdaOriginal)
    patchedJar.set(layout.buildDirectory.file("patched/JDA-android.jar"))
}

dependencies {
    api(project(":shared"))

    jdaOriginal(libs.jda)
    api(files(patchJda.flatMap { it.patchedJar }))
    // JDA's own dependencies (from its POM). opus-java is left out: no voice/music in PocketBot.
    api(libs.slf4j.api)
    api(libs.nv.websocket)
    api(libs.okhttp)
    api(libs.commons.collections4)
    runtimeOnly(libs.trove4j)
    runtimeOnly(libs.jackson.core)
    runtimeOnly(libs.jackson.databind)
    runtimeOnly(libs.tink)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
