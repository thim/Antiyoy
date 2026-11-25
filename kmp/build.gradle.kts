import korlibs.korge.gradle.*

plugins {
    alias(libs.plugins.korge)
}

korge {
    id = "yio.tro.antiyoy.kmp"
    name = "Antiyoy"
    title = "Antiyoy"

    targetJvm()
    targetJs()
    targetWasm()
    // Android/iOS: enable when the SDKs are available on the build machine
    // targetAndroid()
    // targetIos()
}

dependencies {
    add("commonMainApi", project(":core"))
}

// The KorGE Wasm distribution ships without an HTML page; reuse the JS one (it loads antiyoy.js either way).
afterEvaluate {
    tasks.findByName("wasmJsBrowserDistribution")?.doLast {
        copy {
            from(file("web"))
            into(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
        }
    }
}
