plugins { application }

dependencies {
    implementation(project(":core"))
    implementation(project(":client-common"))
    implementation(testFixtures(project(":core")))
}

application { mainClass.set("io.github.playroomproject.mappreview.benchmark.EngineBenchmark") }
