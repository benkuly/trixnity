import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.PluginDependenciesSpecScope
import org.gradle.plugin.use.PluginDependency

// Workaround for plugins that have to be an implementation dependency of buildSrc and thus are
// already on the classpath
fun PluginDependenciesSpecScope.builtin(notation: Provider<PluginDependency>)
    = id(notation.get().pluginId)