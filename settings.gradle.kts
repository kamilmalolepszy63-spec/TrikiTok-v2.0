try {
    val processEnv = Class.forName("java.lang.ProcessEnvironment")
    fun unmap(fieldName: String) {
        val field = processEnv.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(null) as? MutableMap<String, String>
        map?.remove("ANDROID_PREFS_ROOT")
    }
    unmap("theEnvironment")
    unmap("theCaseInsensitiveEnvironment")
} catch (_: Throwable) {
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TrikiTok"
include(":app")
