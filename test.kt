import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
fun test(auth: Auth) {
    val res = auth.signUpWith(Email) { }
}
