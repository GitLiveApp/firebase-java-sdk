import com.google.firebase.FirebaseApp
import org.junit.Before

abstract class FirebaseTest {
    @Before
    fun beforeEach() {
        FirebaseApp.clearInstancesForTest()
    }
}
