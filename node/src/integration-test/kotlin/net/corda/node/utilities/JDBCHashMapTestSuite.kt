package net.corda.node.utilities

import net.corda.testing.node.makeTestDataSourceProperties
import junit.framework.TestSuite
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.io.Closeable
import java.sql.Connection
import java.util.*

@RunWith(Suite::class)
@Suite.SuiteClasses(
        JDBCHashMapTestSuite.MapLoadOnInitFalse::class,
        JDBCHashMapTestSuite.MapLoadOnInitTrue::class,
        JDBCHashMapTestSuite.SetLoadOnInitFalse::class,
        JDBCHashMapTestSuite.SetLoadOnInitTrue::class)
class JDBCHashMapTestSuite {
    companion object {
        lateinit var dataSource: Closeable
        lateinit var transaction: Transaction
        lateinit var database: Database
        lateinit var loadOnInitFalseMap: JDBCHashMap<String, String>
        lateinit var memoryConstrainedMap: JDBCHashMap<String, String>
        lateinit var loadOnInitTrueMap: JDBCHashMap<String, String>
        lateinit var loadOnInitFalseSet: JDBCHashSet<String>
        lateinit var memoryConstrainedSet: JDBCHashSet<String>
        lateinit var loadOnInitTrueSet: JDBCHashSet<String>

        @JvmStatic
        @BeforeClass
        fun before() {
            val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
            dataSource = dataSourceAndDatabase.first
            database = dataSourceAndDatabase.second
            setUpDatabaseTx()
            loadOnInitFalseMap = JDBCHashMap<String, String>("test_map_false", loadOnInit = false)
            memoryConstrainedMap = JDBCHashMap<String, String>("test_map_constrained", loadOnInit = false, maxBuckets = 1)
            loadOnInitTrueMap = JDBCHashMap<String, String>("test_map_true", loadOnInit = true)
            loadOnInitFalseSet = JDBCHashSet<String>("test_set_false", loadOnInit = false)
            memoryConstrainedSet = JDBCHashSet<String>("test_set_constrained", loadOnInit = false, maxBuckets = 1)
            loadOnInitTrueSet = JDBCHashSet<String>("test_set_true", loadOnInit = true)
        }

        @JvmStatic
        @AfterClass
        fun after() {
            closeDatabaseTx()
            dataSource.close()
        }

        @JvmStatic
        fun createMapTestSuite(loadOnInit: Boolean, constrained: Boolean): TestSuite = com.google.common.collect.testing.MapTestSuiteBuilder
                .using(JDBCHashMapTestGenerator(loadOnInit = loadOnInit, constrained = constrained))
                .named("test JDBCHashMap with loadOnInit=$loadOnInit")
                .withFeatures(
                        com.google.common.collect.testing.features.CollectionSize.ANY,
                        com.google.common.collect.testing.features.MapFeature.ALLOWS_ANY_NULL_QUERIES,
                        com.google.common.collect.testing.features.MapFeature.GENERAL_PURPOSE,
                        com.google.common.collect.testing.features.CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                        com.google.common.collect.testing.features.CollectionFeature.KNOWN_ORDER
                )
                // putAll(null) not supported by Kotlin MutableMap interface
                .suppressing(com.google.common.collect.testing.testers.MapPutAllTester::class.java.getMethod("testPutAll_nullCollectionReference"))
                .createTestSuite()

        @JvmStatic
        fun createSetTestSuite(loadOnInit: Boolean, constrained: Boolean): TestSuite = com.google.common.collect.testing.SetTestSuiteBuilder
                .using(JDBCHashSetTestGenerator(loadOnInit = loadOnInit, constrained = constrained))
                .named("test JDBCHashSet with loadOnInit=$loadOnInit")
                .withFeatures(
                        com.google.common.collect.testing.features.CollectionSize.ANY,
                        com.google.common.collect.testing.features.SetFeature.GENERAL_PURPOSE,
                        com.google.common.collect.testing.features.CollectionFeature.SUPPORTS_ITERATOR_REMOVE,
                        com.google.common.collect.testing.features.CollectionFeature.KNOWN_ORDER
                )
                // add/remove/retainAll(null) not supported by Kotlin MutableSet interface
                .suppressing(com.google.common.collect.testing.testers.CollectionAddAllTester::class.java.getMethod("testAddAll_nullCollectionReference"))
                .suppressing(com.google.common.collect.testing.testers.CollectionAddAllTester::class.java.getMethod("testAddAll_nullUnsupported"))
                .suppressing(com.google.common.collect.testing.testers.CollectionAddTester::class.java.getMethod("testAdd_nullUnsupported"))
                .suppressing(com.google.common.collect.testing.testers.CollectionCreationTester::class.java.getMethod("testCreateWithNull_unsupported"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRemoveAllTester::class.java.getMethod("testRemoveAll_nullCollectionReferenceNonEmptySubject"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRemoveAllTester::class.java.getMethod("testRemoveAll_nullCollectionReferenceEmptySubject"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRetainAllTester::class.java.getMethod("testRetainAll_nullCollectionReferenceNonEmptySubject"))
                .suppressing(com.google.common.collect.testing.testers.CollectionRetainAllTester::class.java.getMethod("testRetainAll_nullCollectionReferenceEmptySubject"))
                .createTestSuite()

        private fun setUpDatabaseTx() {
            transaction = TransactionManager.currentOrNew(Connection.TRANSACTION_REPEATABLE_READ)
        }

        private fun closeDatabaseTx() {
            transaction.commit()
            transaction.close()
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=false, constrained = false).
     */
    class MapLoadOnInitFalse {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(false, false)
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=false, constrained = true).
     */
    class MapConstained {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(false, true)
        }
    }

    /**
     * Guava test suite generator for JDBCHashMap(loadOnInit=true, constrained = false).
     */
    class MapLoadOnInitTrue {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createMapTestSuite(true, false)
        }
    }

    /**
     * Generator of map instances needed for testing.
     */
    class JDBCHashMapTestGenerator(val loadOnInit: Boolean, val constrained: Boolean) : com.google.common.collect.testing.TestStringMapGenerator() {
        override fun create(elements: Array<Map.Entry<String, String>>): Map<String, String> {
            val map = if (loadOnInit) loadOnInitTrueMap else if(constrained) memoryConstrainedMap else loadOnInitFalseMap
            map.clear()
            map.putAll(elements.associate { Pair(it.key, it.value) })
            return map
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=false, constrained = false).
     */
    class SetLoadOnInitFalse {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(false, false)
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=false, constrained = true).
     */
    class SetConstrained {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(false, true)
        }
    }

    /**
     * Guava test suite generator for JDBCHashSet(loadOnInit=true, constrained = false).
     */
    class SetLoadOnInitTrue {
        companion object {
            @JvmStatic
            fun suite(): TestSuite = createSetTestSuite(true, false)
        }
    }

    /**
     * Generator of set instances needed for testing.
     */
    class JDBCHashSetTestGenerator(val loadOnInit: Boolean, val constrained: Boolean) : com.google.common.collect.testing.TestStringSetGenerator() {
        override fun create(elements: Array<String>): Set<String> {
            val set = if (loadOnInit) loadOnInitTrueSet else if(constrained) memoryConstrainedSet else loadOnInitFalseSet
            set.clear()
            set.addAll(elements)
            return set
        }
    }

    /**
     * Test that the contents of a map can be reloaded from the database.
     *
     * If the Map reloads, then so will the Set as it just delegates.
     */
    class MapCanBeReloaded {
        private val ops = listOf(Triple(AddOrRemove.ADD, "A", "1"),
                Triple(AddOrRemove.ADD, "B", "2"),
                Triple(AddOrRemove.ADD, "C", "3"),
                Triple(AddOrRemove.ADD, "D", "4"),
                Triple(AddOrRemove.ADD, "E", "5"),
                Triple(AddOrRemove.REMOVE, "A", "6"),
                Triple(AddOrRemove.ADD, "G", "7"),
                Triple(AddOrRemove.ADD, "H", "8"),
                Triple(AddOrRemove.REMOVE, "D", "9"),
                Triple(AddOrRemove.ADD, "C", "10"))

        private fun applyOpsToMap(map: MutableMap<String, String>): MutableMap<String, String> {
            for (op in ops) {
                if (op.first == AddOrRemove.ADD) {
                    map[op.second] = op.third
                } else {
                    map.remove(op.second)
                }
            }
            return map
        }

        private val transientMapForComparison = applyOpsToMap(LinkedHashMap())

        lateinit var dataSource: Closeable
        lateinit var database: Database

        @Before
        fun before() {
            val dataSourceAndDatabase = configureDatabase(makeTestDataSourceProperties())
            dataSource = dataSourceAndDatabase.first
            database = dataSourceAndDatabase.second
        }

        @After
        fun after() {
            dataSource.close()
        }


        @Test
        fun `fill map and check content after reconstruction`() {
            databaseTransaction(database) {
                val persistentMap = JDBCHashMap<String, String>("the_table")
                // Populate map the first time.
                applyOpsToMap(persistentMap)
                assertThat(persistentMap.entries).containsExactly(*transientMapForComparison.entries.toTypedArray())
            }
            databaseTransaction(database) {
                val persistentMap = JDBCHashMap<String, String>("the_table", loadOnInit = false)
                assertThat(persistentMap.entries).containsExactly(*transientMapForComparison.entries.toTypedArray())
            }
            databaseTransaction(database) {
                val persistentMap = JDBCHashMap<String, String>("the_table", loadOnInit = true)
                assertThat(persistentMap.entries).containsExactly(*transientMapForComparison.entries.toTypedArray())
            }
        }
    }
}