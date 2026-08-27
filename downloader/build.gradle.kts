plugins {
    id("com.gradleup.shadow")
    java
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.119-stable")
    implementation(project(":common"))
}

tasks.jar {
    archiveClassifier.set("unshaded")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.shadowJar {
    archiveBaseName.set("gitsync-downloader")
    archiveClassifier.set("")
    relocate("com.google.gson", "net.bsxray.gitsync.libs.gson")
    mergeServiceFiles()
}
