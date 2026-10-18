package app.libretv.tv
import org.junit.Assert.assertEquals
import org.junit.Test
class GridNavigationTest {
 @Test fun incompleteLastRowUsesNearestCell(){assertEquals(4,Core.gridDown(3,5,4));assertEquals(2,Core.gridDown(1,3,2))}
 @Test fun bottomRowStaysAndRegularRowsKeepColumn(){assertEquals(4,Core.gridDown(4,5,4));assertEquals(6,Core.gridDown(2,39,4));assertEquals(37,Core.gridDown(37,39,4))}
}
