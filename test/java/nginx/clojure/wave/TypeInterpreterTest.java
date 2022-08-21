package nginx.clojure.wave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;

import nginx.clojure.asm.Type;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;


/**
 * The some testing code is copied from 
 * https://github.com/Grundlefleck/ASM-NonClassloadingSimpleVerifier/blob/master/src/test/java/org/objectweb/asm/tree/analysis/TypeHierarchyUnitTest.java
 * But the testing target is quite different about their implementation.
 */
public class TypeInterpreterTest {
	
	MethodDatabase db;
	
	TypeInterpreter ti;
	
	public Type JAVA_LANG_OBJECT = Type.getType(Object.class);

	@Before
	public void setUp() throws Exception {
		db = new MethodDatabase(Thread.currentThread().getContextClassLoader());
		ti = new TypeInterpreter(db);
	}

	@After
	public void tearDown() throws Exception {
	}
    
	
	public Type getType(@SuppressWarnings("rawtypes") Class c) {
		return Type.getType(c);
	}
    
	@Test
    public void testSuperClassOfConcreteClassExtendingObjectImplicitlyIsTypeRepresentingJavaLangObject() {
        assertEquals(JAVA_LANG_OBJECT, ti.fetchSuperClass(getType(UnrelatedType.class)));
    }

	@Test
    public void testSuperClassOfSubclass() {
        assertEquals(getType(Superclass.class), ti.fetchSuperClass(getType(Subclass.class)));
    }

	@Test
    public void testSuperClassOfInterfaceWithNoSuperInterfaceIsObject() {
        assertEquals(JAVA_LANG_OBJECT, ti.fetchSuperClass(getType(Interface.class)));
    }

	@Test
    public void testSuperClassOfSubInterfaceIsJavaLangObject() {
        assertEquals(JAVA_LANG_OBJECT, ti.fetchSuperClass(getType(SubInterface.class)));
    }
    
	@Test
    public void testSuperclassOfArrayClassHasSameSemanticsAsJavaLangClass_fetchSuperClass() throws Exception {
        assertEquals(getType(Object[].class.getSuperclass()), ti.fetchSuperClass(getType(Object[].class)));
        assertEquals(getType(Interface[].class.getSuperclass()), ti.fetchSuperClass(getType(Interface[].class)));
        assertEquals(getType(Superclass[].class.getSuperclass()), ti.fetchSuperClass(getType(Superclass[].class)));
    }
    
	@Test
    public void testClassIsAssignableFromItself() {
        assertIsAssignableFrom(AssignableFromItself.class, AssignableFromItself.class);
    }

	@Test
    public void testClassIsNotAssignableToUnrelatedType() {
        assertIsNotAssignableFrom(AssignableFromItself.class, UnrelatedType.class);
        assertIsNotAssignableFrom(UnrelatedType.class, AssignableFromItself.class);
    }
    
	@Test
    public void testSuperclassIsAssignableFromSubclass() throws Exception {
        assertIsAssignableFrom(Superclass.class, Subclass.class);
    }
    
	@Test
    public void testIndirectSubclassIsAssignableToSuperclass() throws Exception {
        assertIsAssignableFrom(Superclass.class, SubSubclass.class);
    }
    
	@Test
    public void testSubclassIsNotAssignableToOtherClassWithSameSuperclass() throws Exception {
        assertIsNotAssignableFrom(Subclass.class, OtherSubclass.class);
    }

	@Test
    public void testSubclassIsNotAssignableFromSuperclass() throws Exception {
        assertIsNotAssignableFrom(Subclass.class, Superclass.class);
    }
    
	@Test
    public void testInterfaceIsAssignableFromImplementingClass() throws Exception {
        assertIsAssignableFrom(Interface.class, ImplementsInterface.class);
    }
    
	@Test
    public void testInterfaceIsAssignableFromSubclassOfImplementingClass() throws Exception {
        assertIsAssignableFrom(Interface.class, ExtendsImplementsInterface.class);
        assertIsNotAssignableFrom(ExtendsImplementsInterface.class, Interface.class);
    }
    
	@Test
    public void testSuperInterfaceIsAssignableFromSubInterface() throws Exception {
        assertIsAssignableFrom(SuperInterface.class, SubInterface.class);
        assertIsNotAssignableFrom(SubInterface.class, SuperInterface.class);
    }
    
	@Test
    public void testImplementingClassIsNotAssignableFromInterface() throws Exception {
        assertIsNotAssignableFrom(ImplementsInterface.class, Interface.class);
    }
    
	@Test
    public void testObjectIsAssignableFromAnything() throws Exception {
        assertIsAssignableFrom(Object.class, Superclass.class);
        assertIsAssignableFrom(Object.class, Subclass.class);
        assertIsAssignableFrom(Object.class, Interface.class);
        assertIsAssignableFrom(Object.class, SubInterface.class);
    }
    
	@Test
    public void testAllImplementedInterfacesAreAssignableFromImplementingClass() throws Exception {
        assertIsAssignableFrom(Interface.class, ImplementsSeveralInterfaces.class);
        assertIsAssignableFrom(SubInterface.class, ImplementsSeveralInterfaces.class);
        assertIsAssignableFrom(SuperInterface.class, ImplementsSeveralInterfaces.class);
        assertIsNotAssignableFrom(OtherImplementsInterface.class, ImplementsSeveralInterfaces.class);
    }
    
	@Test
    public void testInterfaceIsAssignableFromClassWithSuperclassOutwithInterfaceHierarchy() throws Exception {
        assertIsAssignableFrom(SuperInterface.class, ExtendsClassOutwithInterfaceHierarchy.class);
    }
    
	@Test
    public void testArrayTypeAssignment() throws Exception {
        assertIsAssignableFrom(Object.class, Interface[].class);
        assertIsAssignableFrom(Cloneable.class, Interface[].class);
        assertIsAssignableFrom(Serializable.class, Interface[].class);
        assertIsAssignableFrom(Object[].class, Interface[].class);
        assertIsAssignableFrom(Object[].class, Interface[].class);
        assertIsAssignableFrom(Interface[].class, Interface[].class);
        assertIsAssignableFrom(Interface[].class, ImplementsInterface[].class);
        assertIsNotAssignableFrom(ImplementsInterface[].class, Interface[].class);
        assertIsAssignableFrom(Interface[].class, ExtendsImplementsInterface[].class);
        assertIsAssignableFrom(Object[].class, Superclass[].class);
        assertIsAssignableFrom(Object[].class, Subclass[].class);
        assertIsAssignableFrom(Superclass[].class, Subclass[].class);
        assertIsNotAssignableFrom(Subclass[].class, Superclass[].class);
    }
    
	@Test
    public void testArrayDimensionAssignment() throws Exception {
        assertIsAssignableFrom(Object.class, Object[].class);
        assertIsNotAssignableFrom(Object[].class, Object.class);
        assertIsAssignableFrom(Object.class, Interface[].class);
        assertIsAssignableFrom(Object[].class, Interface[][].class);
        assertIsAssignableFrom(Object[][].class, Interface[][].class);
        assertIsNotAssignableFrom(Interface.class, Interface[].class);
        assertIsNotAssignableFrom(Interface[].class, Interface.class);
        assertIsNotAssignableFrom(Interface[].class, Interface[][].class);
        assertIsNotAssignableFrom(Interface[][].class, Interface[].class);
    }
    
	@Test
    public void testAnonymousInnerClasses() throws Exception {
        assertIsAssignableFrom(Interface.class, new Interface() { }.getClass());
        assertIsNotAssignableFrom(new Interface() { }.getClass(), Interface.class);
    }

	@Test
    public void testAssignmentOfPrimitiveArrayTypes() throws Exception {
        assertIsAssignableFrom(boolean[].class, boolean[].class);
        assertIsAssignableFrom(byte[].class, byte[].class);
        assertIsAssignableFrom(char[].class, char[].class);
        assertIsAssignableFrom(short[].class, short[].class);
        assertIsAssignableFrom(int[].class, int[].class);
        assertIsAssignableFrom(long[].class, long[].class);
        assertIsAssignableFrom(float[].class, float[].class);
        assertIsAssignableFrom(double[].class, double[].class);
        assertIsNotAssignableFrom(Object[].class, float[].class);
    }

	@Test
    public void testGetCommonSuperClass_shouldBeObjectForUnrelatedClasses() throws Exception {
        assertCommonSuperclass(Object.class, Superclass.class, UnrelatedType.class);
    }

	@Test
    public void testGetCommonSuperClass_shouldBeClosestSharedSuperclass() throws Exception {
        assertCommonSuperclass(Superclass.class, Subclass.class, OtherSubclass.class);
    }

	@Test
    public void testGetCommonSuperClass_shouldBeSameTypeWhenBothAreEqual() throws Exception {
        assertCommonSuperclass(UnrelatedType.class, UnrelatedType.class, UnrelatedType.class);
    }

    @Test
    public void testGetCommonSuperClass_shouldBeSuperclassOfTwoGivenTypes() throws Exception {
        assertCommonSuperclass(Superclass.class, Superclass.class, Subclass.class);
    }

    @Test
    public void testGetCommonSuperClass_shouldBeObjectForUnrelatedInterfaces() throws Exception {
        assertCommonSuperclass(Object.class, Interface.class, OtherInterface.class);
    }

//    @Test
//    public void fails_returnsObject_testGetCommonSuperClass_shouldBeClosestSharedInterface() throws Exception {
//        assertCommonSuperclass(SubInterface.class, ImplementsSeveralInterfaces.class, AlsoImplementsSubInterface.class);
//    }

    @Test
    public void testGetCommonSuperClass_shouldBeObjectForTwoInterfacesWhoShareCommonSuperInterface() throws Exception {
        assertCommonSuperclass(Object.class, SubInterface.class, OtherSubInterface.class);
    }
    
    private void assertIsAssignableFrom(Class<?> to, Class<?> from) {
    	
        assertTrue("Assertion is not consistent with Class.isAssignableFrom", to.isAssignableFrom(from));
        Type toType = Type.getType(to);
        Type fromType = Type.getType(from);
        assertTrue("Type Hierarchy visitor is not consistent with Class.isAssignableFrom", 
                ti.checkAssignableFrom(toType, fromType));
    }

    private void assertIsNotAssignableFrom(Class<?> to, Class<?> from) {
        assertFalse("Assertion is not consistent with Class.isAssignableFrom", to.isAssignableFrom(from));
        Type toType = Type.getType(to);
        Type fromType = Type.getType(from);
        assertFalse("Type Hierarchy visitor is not consistent with Class.isAssignableFrom", 
                ti.checkAssignableFrom(toType, fromType));
    }
    
    private void assertCommonSuperclass(Class<?> expected, Class<?> first, Class<?> second) {
        assertEquals(slashedName(expected),
                db.getCommonSuperClass(slashedName(first), slashedName(second)));
        assertEquals(slashedName(expected),
                db.getCommonSuperClass(slashedName(second), slashedName(first)));
    }

    private String slashedName(Class<?> cls) {
        return cls.getName().replace(".", "/");
    }
    
    public static class AssignableFromItself { }
    
    public static class UnrelatedType { }
    
    public static class Superclass { }
    public static class Subclass extends Superclass { }
    public static class OtherSubclass extends Superclass { }
    public static class SubSubclass extends Subclass { }
    
    public static interface Interface { }
    public static interface OtherInterface { }
    public static class ImplementsInterface implements Interface { }
    public static class ExtendsImplementsInterface extends ImplementsInterface { }
    
    public static interface SuperInterface { }
    public static interface SubInterface extends SuperInterface { }
    public static interface OtherSubInterface { }
    
    public static class ImplementsSeveralInterfaces implements Interface, SubInterface { }
    public static class OtherImplementsInterface implements Interface { }
    public static class AlsoImplementsSubInterface implements SubInterface { }
    
    public static class ExtendsClassOutwithInterfaceHierarchy extends UnrelatedType implements SubInterface { }
    


}
