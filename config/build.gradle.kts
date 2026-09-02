dependencies {
    api(project(":core"))
    api(project(":pregen-common"))
    implementation("com.google.code.gson:gson:2.13.1")
    compileOnly("com.google.errorprone:error_prone_annotations:2.38.0")
}
