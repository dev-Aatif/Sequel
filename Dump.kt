import io.github.jan.supabase.auth.Auth
import kotlin.reflect.full.memberProperties

fun main() {
    val kClass = Auth.Config::class
    kClass.memberProperties.forEach { println(it.name) }
}
