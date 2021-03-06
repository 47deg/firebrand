/*
 * Copyright (C) 2012 47 Degrees, LLC
 * http://47deg.com
 * hello@47deg.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.firebrandocm.tests;

import org.apache.commons.beanutils.PropertyUtils;
import org.firebrandocm.dao.ClassMetadata;
import org.firebrandocm.dao.Query;
import org.firebrandocm.dao.cql.clauses.Predicate;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static junit.framework.Assert.*;
import static junit.framework.Assert.assertEquals;
import static org.firebrandocm.dao.cql.QueryBuilder.*;
import static org.firebrandocm.dao.cql.QueryBuilder.from;

/**
 *
 */
public class PersistenceOperationTest extends HectorAbstractTestCase {

	@BeforeClass
	public static void init() throws Exception {
		//initWithClasses(FirstEntity.class, SecondEntity.class, FirstEntityCounter.class);
		initWithClasses("org.firebrandocm.tests");
	}

	/**
	 *
	 * @return a random string on every call
	 */
	private static String rds() {
		return UUID.randomUUID().toString();
	}

	@Test
	public void testAutoGeneratedId() {
		FirstEntity entity = factory.getInstance(FirstEntity.class);
		factory.persist(entity);
		assertNotNull(entity.getId());
	}

	@Test
	public void testCount() {
		long amount = 5L;
		for (int i = 0; i < amount; i++) {
			FirstEntity entity = factory.getInstance(FirstEntity.class);
			factory.persist(entity);
		}
		long count = factory.getSingleResult(Long.class, Query.get(select(count(), from(FirstEntity.class))));
		assertEquals(count, amount);
	}

	@Test
	public void testOrder() {
		int amount = 10;
		List<FirstEntity> creationOrderedEntities = new ArrayList<FirstEntity>(amount);
		for (int i = 0; i < amount; i++) {
			FirstEntity entity = factory.getInstance(FirstEntity.class);
			factory.persist(entity);
			creationOrderedEntities.add(entity);
		}
		List<FirstEntity> allEntities = factory.getResultList(FirstEntity.class, Query.get(select(allColumns(), from(FirstEntity.class))));

		assertEquals(amount, creationOrderedEntities.size());
		assertEquals(amount, allEntities.size());
		for (int i = 0; i < amount; i++) {
			FirstEntity entity = creationOrderedEntities.get(i);
			FirstEntity other = allEntities.get(i);
			assertEquals(entity, other);
		}
	}

	@Test
	public void testMappedEntity() {
		SecondEntity secondEntity = factory.getInstance(SecondEntity.class);
		factory.persist(secondEntity);
		FirstEntity entity = factory.getInstance(FirstEntity.class);
		entity.setMappedEntity(secondEntity);
		factory.persist(entity);
		assertNotNull(entity.getId());
		assertNotNull(secondEntity.getId());
		FirstEntity loadedEntity = factory.get(FirstEntity.class, entity.getId());
		assertEquals(secondEntity, loadedEntity.getMappedEntity());
		assertEquals(secondEntity.getId(), loadedEntity.getMappedEntity().getId());
	}

    @Test
    public void testMappedRecursiveEntity() {
        FirstEntity firstEntity = factory.getInstance(FirstEntity.class);
        SecondEntity secondEntity = factory.getInstance(SecondEntity.class);
        factory.persist(firstEntity);
        factory.persist(secondEntity);
        firstEntity.setMappedEntity(secondEntity);
        secondEntity.setMappedFirstEntity(firstEntity);
        factory.persist(firstEntity);
        factory.persist(secondEntity);
        assertEquals(firstEntity, secondEntity.getMappedFirstEntity());
        assertEquals(secondEntity, firstEntity.getMappedEntity());
    }

	@Test
	public void testMappedCollection() {
		int amount = 5;
		FirstEntity entity = factory.getInstance(FirstEntity.class);
		List<SecondEntity> listProperty = new ArrayList<SecondEntity>();
		for (int i = 0; i < amount; i++) {
			SecondEntity secondEntity = factory.getInstance(SecondEntity.class);
			factory.persist(secondEntity);
			listProperty.add(secondEntity);
		}
		entity.setListProperty(listProperty);
		factory.persist(entity);
		FirstEntity loadedEntity = factory.get(FirstEntity.class, entity.getId());
		assertNotNull("null list loaded but expected not null", loadedEntity.getListProperty());
		assertEquals(amount, loadedEntity.getListProperty().size());
		//test loaded equals and order
		for (int i = 0; i < amount; i++) {
			assertEquals(entity.getListProperty().get(i), loadedEntity.getListProperty().get(i));
		}
	}

    @Test
    public void testEnumProperty() {
        FirstEntity firstEntity = factory.getInstance(FirstEntity.class);
        firstEntity.setTestEnum(TestEnum.A);
        factory.persist(firstEntity);
        FirstEntity loadedEntity = factory.get(FirstEntity.class, firstEntity.getId());
        assertEquals(TestEnum.A, loadedEntity.getTestEnum());
    }

	@Test
	public void testEmbeddedEntity() {
		OtherEntity otherEntity = new OtherEntity();
		otherEntity.setFirstProperty(rds());
		otherEntity.setNullProperty(null);
		FirstEntity entity = factory.getInstance(FirstEntity.class);
		entity.setOtherEntity(otherEntity);
		factory.persist(entity);
		FirstEntity loadedEntity = factory.get(FirstEntity.class, entity.getId());
		assertEquals(otherEntity, loadedEntity.getOtherEntity());
		assertNull(loadedEntity.getOtherEntity().getNullProperty());
		assertNull(otherEntity.getNullProperty());
	}

	@Test
	public void testNestedEmbeddedEntity() {
		OtherEntity otherEntity = new OtherEntity();
		otherEntity.setFirstProperty(rds());
		otherEntity.setNullProperty(null);
		ThirdEntity thirdEntity = new ThirdEntity();
		thirdEntity.setSomeProperty(rds());
		otherEntity.setNestedThirdProperty(thirdEntity);
		FirstEntity entity = factory.getInstance(FirstEntity.class);
		entity.setOtherEntity(otherEntity);
		factory.persist(entity);
		FirstEntity loadedEntity = factory.get(FirstEntity.class, entity.getId());
		assertEquals(otherEntity, loadedEntity.getOtherEntity());
		assertEquals(thirdEntity, loadedEntity.getOtherEntity().getNestedThirdProperty());
		assertEquals(thirdEntity.getSomeProperty(), loadedEntity.getOtherEntity().getNestedThirdProperty().getSomeProperty());
	}

	@Test
	public void testCounterIncrease() {
		long counterInitialValue = 10;
		long counterIncreaseValue = +2;
		long afterPersistCounterExpectedValue = +2;
		long afterPersistCounterIncreaseExpectedValue = 0;
		FirstEntityCounter entity = new FirstEntityCounter();
		entity.setCounterProperty(counterInitialValue);
		entity.setCounterPropertyIncreaseBy(counterIncreaseValue);
		factory.persist(entity);
		assertEquals(afterPersistCounterIncreaseExpectedValue, entity.getCounterPropertyIncreaseBy());
		FirstEntityCounter loadedEntity = factory.get(entity.getClass(), entity.getId());
		assertEquals(afterPersistCounterExpectedValue, loadedEntity.getCounterProperty());
		assertEquals(afterPersistCounterIncreaseExpectedValue, loadedEntity.getCounterPropertyIncreaseBy());
	}

	@Test
	public void testSerializedObjectColumn() {
		FirstEntity entity = new FirstEntity();
		List<Object> list = Arrays.<Object>asList("1", 2, 3.0, 4L);
		entity.setListSerializedAsBytes(new LinkedList<Object>(list));
		factory.persist(entity);
		FirstEntity loadedEntity = factory.get(FirstEntity.class, entity.getId());
		assertEquals(entity.getListSerializedAsBytes(), loadedEntity.getListSerializedAsBytes());
	}

	@Test
	public void testColumnEagerAccess() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		FirstEntity firstEntity = new FirstEntity();
		firstEntity.setDescription(rds());
		factory.persist(firstEntity);
		testPropertyEagerAccess(FirstEntity.class, firstEntity.getId(), "description");
	}

	@Test
	public void testMappedEntityEagerAccess() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		FirstEntity firstEntity = new FirstEntity();
		SecondEntity secondEntity = new SecondEntity();
		factory.persist(secondEntity);
		firstEntity.setMappedEntity(secondEntity);
		factory.persist(firstEntity);
		testPropertyEagerAccess(FirstEntity.class, firstEntity.getId(), "mappedEntity");
	}

	@Test
	public void testMappedCollectionEagerAccess() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		FirstEntity firstEntity = new FirstEntity();
		List<SecondEntity> listProperty = new ArrayList<SecondEntity>();
		SecondEntity secondEntity = new SecondEntity();
		factory.persist(secondEntity);
		listProperty.add(secondEntity);
		firstEntity.setSecondEagerListProperty(listProperty);
		factory.persist(firstEntity);
		testPropertyEagerAccess(FirstEntity.class, firstEntity.getId(), "secondEagerListProperty");
	}

	@Test
	public void testColumnLazyAccess() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		FirstEntity firstEntity = new FirstEntity();
		firstEntity.setHugeDescription(rds());
		factory.persist(firstEntity);
		testPropertyLazyAccess(FirstEntity.class, firstEntity.getId(), "hugeDescription");
	}

	@Test
	public void testMappedEntityLazyAccess() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		FirstEntity firstEntity = new FirstEntity();
		SecondEntity secondEntity = new SecondEntity();
		factory.persist(secondEntity);
		firstEntity.setSecondLazyMappedEntity(secondEntity);
		factory.persist(firstEntity);
		testPropertyLazyAccess(FirstEntity.class, firstEntity.getId(), "secondLazyMappedEntity");
	}

	@Test
	public void testMappedCollectionLazyAccess() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		FirstEntity firstEntity = new FirstEntity();
		List<SecondEntity> listProperty = new ArrayList<SecondEntity>();
		SecondEntity secondEntity = new SecondEntity();
		factory.persist(secondEntity);
		listProperty.add(secondEntity);
		firstEntity.setListProperty(listProperty);
		factory.persist(firstEntity);
		testPropertyLazyAccess(FirstEntity.class, firstEntity.getId(), "listProperty");
	}

    @Test
    public void testEQByteArrayProperties() throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, UnsupportedEncodingException {
        String test = UUID.randomUUID().toString();
        FirstEntity firstEntity = new FirstEntity();
        firstEntity.setSomeBytes(test.getBytes());
        factory.persist(firstEntity);
        FirstEntity loadedEntity = factory.get(FirstEntity.class, firstEntity.getId());
        assertEquals(new String(loadedEntity.getSomeBytes(), "UTF-8"), test);
    }

	@Test
	public void testEQLongIndexedProperty() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		testIndexedPropertyEQ(FirstEntity.class, new HashMap<String, Object>(){{
			put("phone", 123412341234L);
		}});
	}

	@Test
	public void testEQStringIndexedProperty() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		testIndexedPropertyEQ(FirstEntity.class, new HashMap<String, Object>(){{
			put("name", rds());
			put("description", "description'quoted");
		}});
	}

	@Test
	public void testEQDoubleIndexedProperty() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		testIndexedPropertyEQ(FirstEntity.class, new HashMap<String, Object>(){{
			put("score", 76.98);
		}});
	}

	@Test
	public void testRangeLongIndexedProperty() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		testIndexedPropertyRanges(FirstEntity.class, "phone", 1L, 0L, 2L);
	}

	@Test
	public void testRangeStringIndexedProperty() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		testIndexedPropertyRanges(FirstEntity.class, "name", "b", "a", "z");
	}

	@Test
	public void testRangeDoubleIndexedProperty() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		testIndexedPropertyRanges(FirstEntity.class, "score", 1.0, 0.0, 2.0);
	}

	@Test
	public void testRangeDateIndexedProperty() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		Date date = new Date();
		testIndexedPropertyRanges(FirstEntity.class, "date", date, new Date(date.getTime() - 1000), new Date(date.getTime() + 1000));
	}

	@Test
	public void testNamedQuery() {
		assertNotNull(ClassMetadata.getNullSafeNamedQuery(FirstEntity.QUERY_ALL_ENTITIES));
		FirstEntity firstEntity = new FirstEntity();
		factory.persist(firstEntity);
		FirstEntity loadedEntity = factory.getSingleResult(FirstEntity.class, Query.get(FirstEntity.QUERY_ALL_ENTITIES));
		assertEquals(loadedEntity, firstEntity);
	}

	@Test
	public void testNamedQueryWithParams() {
		assertNotNull(ClassMetadata.getNullSafeNamedQuery(FirstEntity.QUERY_ALL_ENTITIES_WITH_PARAMS));
		final FirstEntity firstEntity = new FirstEntity();
		factory.persist(firstEntity);
		FirstEntity loadedEntity = factory.getSingleResult(FirstEntity.class, Query.get(FirstEntity.QUERY_ALL_ENTITIES_WITH_PARAMS, new HashMap<String, Object>(){{
			put("key", firstEntity.getId());
		}}));
		assertEquals(loadedEntity, firstEntity);
	}

	@Test
	public void testPrePersistListener() {
		final FirstEntity firstEntity = new FirstEntity();
		factory.persist(firstEntity);
		assertNotNull(firstEntity.getPrePersistProperty());
	}

	@Test
	public void testDeletion() {
		FirstEntity firstEntity = new FirstEntity();
		firstEntity.setName(rds());
		factory.persist(firstEntity);
		List<?> results = factory.getResultList(FirstEntity.class, Query.get(select(
				allColumns(),
				from(FirstEntity.class)
		)));
		assertEquals(1, results.size());
		factory.remove(firstEntity);
		results = factory.getResultList(FirstEntity.class, Query.get(select(
				allColumns(),
				from(FirstEntity.class)
		)));
		assertEquals(0, results.size());
	}

	@Test
	public void testMappedCounter() {
		FirstEntity entity = new FirstEntity();
		entity.setName(rds());
		factory.persist(entity);
		FirstEntityCounter counter = new FirstEntityCounter();
		factory.persist(counter);
		SecondEntity secondEntity = new SecondEntity();
		factory.persist(secondEntity);
		entity.setCounter(counter);
		entity.setMappedEntity(secondEntity);
		factory.persist(entity);
		FirstEntity loadedEntity = factory.get(FirstEntity.class, entity.getId());
		assertNotNull(loadedEntity.getCounter());
		assertEquals(counter, loadedEntity.getCounter());
		assertEquals(entity.getCounter(), loadedEntity.getCounter());
	}

	@Test
	public void testPagination() {
		//todo implement composite types once supported in CQL in 1.1 for pagination and ordering support via column composite names and wide rows.
		int amount = 10;
		int start  = 2;
		int limit = 5;
		String startKey = null;
		String endKey = null;
		List<String> allKeys = new ArrayList<String>(amount);
		for (int i = 0; i < amount; i++) {
			FirstEntity entity = new FirstEntity();
			factory.persist(entity);
			if (i == start) {
				startKey = entity.getId();
			}

			if (i == start + limit - 1) {
				endKey = entity.getId();
			}
			allKeys.add(entity.getId());
		}
		Query query = Query.get(select(allColumns(), from(FirstEntity.class), where(range(startKey, endKey)), limit(limit)));
		log.debug(query.getQuery());
		List<FirstEntity> results = factory.getResultList(FirstEntity.class, query);
		assertEquals(limit, results.size());
		assertEquals(startKey, results.get(0).getId());
		assertEquals(endKey, results.get(results.size() - 1).getId());

		query = Query.get(select(allColumns(), from(FirstEntity.class), where(startAt(startKey)), limit(limit)));
		log.debug(query.getQuery());
		results = factory.getResultList(FirstEntity.class, query);
		assertEquals(limit, results.size());
		assertEquals(startKey, results.get(0).getId());
		assertEquals(endKey, results.get(results.size() - 1).getId());
	}


	private void testIndexedPropertyEQ(Class<?> entityClass, Map<String, Object> params) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		Object entity = factory.getInstance(entityClass);
		List<Predicate> predicates = new ArrayList<Predicate>();
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			String property = entry.getKey();
			Object value = entry.getValue();
			PropertyUtils.setProperty(entity, property, value);
			predicates.add(eq(property, value));
		}
		factory.persist(entity);
		Object loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(predicates.toArray(new Predicate[predicates.size()])))
		));
		assertEquals(entity, loadedEntity);
	}

	private void testIndexedPropertyRanges(Class<?> entityClass, String property, Object value, Object lesser, Object upper) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
		Object entity = factory.getInstance(entityClass);
		PropertyUtils.setProperty(entity, property, value);
		factory.persist(entity);
		Object loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(type(FirstEntity.class), gt(property, lesser))
		)));
		assertEquals(entity, loadedEntity);
		loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(type(FirstEntity.class), gte(property, lesser)))
		));
		assertEquals(entity, loadedEntity);
		loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(type(FirstEntity.class), gte(property, value))
		)));
		assertEquals(entity, loadedEntity);
		loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(type(FirstEntity.class), lt(property, upper))
		)));
		assertEquals(entity, loadedEntity);
		loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(type(FirstEntity.class), lte(property, upper))
		)));
		assertEquals(entity, loadedEntity);
		loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(type(FirstEntity.class), lte(property, value))
		)));
		assertEquals(entity, loadedEntity);
		loadedEntity = factory.getSingleResult(entityClass, Query.get(select(
				allColumns(),
				from(entityClass),
				where(type(FirstEntity.class), between(property, lesser, upper))
		)));
		assertEquals(entity, loadedEntity);
	}

	private void testPropertyLazyAccess(Class<?> entityClass, String id, String property) throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Object loadedEntity = factory.get(entityClass, id);
		Field privateField = entityClass.getDeclaredField(property);
		privateField.setAccessible(true);
		Object fieldValue = privateField.get(loadedEntity);
		assertTrue("expected null or size 0 since getter has not been invoked due to lazy loading", isNullPropertyOrEmptyCollection(fieldValue));
		Object value = PropertyUtils.getProperty(loadedEntity, property);
		assertTrue("expected not null or size > 0  after reading property via getter and property has been lazy loaded", !isNullPropertyOrEmptyCollection(value));
		fieldValue = privateField.get(loadedEntity);
		value = PropertyUtils.getProperty(loadedEntity, property);
		assertTrue("expected not null or size > 0  after reading property via getter and property has been lazy loaded", !isNullPropertyOrEmptyCollection(fieldValue));
		assertSame("expected same memory reference since collection was already loaded", fieldValue, value);
	}

	private void testPropertyEagerAccess(Class<?> entityClass, String id, String property) throws NoSuchFieldException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		Object loadedEntity = factory.get(entityClass, id);
		Field privateField = entityClass.getDeclaredField(property);
		privateField.setAccessible(true);
		Object fieldValue = privateField.get(loadedEntity);
		assertTrue("expected not null or size > 0 since collection should be eagerly loaded", !isNullPropertyOrEmptyCollection(fieldValue));
		Object value = PropertyUtils.getProperty(loadedEntity, property);
		assertSame("expected same memory reference since collection was already loaded", fieldValue, value);
	}

	private static boolean isNullPropertyOrEmptyCollection(Object value) {
		boolean match = value == null;
		if (!match) {
			if (Collection.class.isAssignableFrom(value.getClass())) {
				match = ((Collection)value).size() == 0;
			}
		}
		return match;
	}

	@Test
	public void testPropertyEquality() {
		String string = rds();
		Date date = new Date();
		Long longNumber = 3L;
		Double score = 99.0;
		SecondEntity secondEntity = factory.getInstance(SecondEntity.class);
		factory.persist(secondEntity);

		FirstEntity entity = factory.getInstance(FirstEntity.class);
		entity.setName(string);
		entity.setDate(date);
		entity.setPhone(longNumber);
		entity.setScore(score);
		entity.setMappedEntity(secondEntity);
		factory.persist(entity);

		assertNotNull(entity.getId());

		FirstEntity loadedEntity = factory.get(FirstEntity.class, entity.getId());
		assertEquals(string, loadedEntity.getName());
		assertEquals(date, loadedEntity.getDate());
		assertEquals(longNumber, loadedEntity.getPhone());
		assertEquals(score, loadedEntity.getScore());
		assertEquals(secondEntity, loadedEntity.getMappedEntity());
	}

	@Test
	public void testPersistence() throws Exception {

		FirstEntity a = factory.getInstance(FirstEntity.class);
		a.setName(rds());
		a.setDescription(rds());
		a.setPhone(1L);
		a.setScore(3.0);
		a.setDate(new Date());
		a.setOtherDate(new Date(a.getDate().getTime() - 1000));
		a.setChangedColumnName(rds());
		factory.persist(a);


		//associations
		OtherEntity c = factory.getInstance(OtherEntity.class);
		c.setFirstProperty(rds());

		ThirdEntity thirdEntity = factory.getInstance(ThirdEntity.class);
		thirdEntity.setSomeProperty(rds());

		FourthEntity fourthEntity = factory.getInstance(FourthEntity.class);
		fourthEntity.setSomeProperty(rds());

		thirdEntity.setFourthEntity(fourthEntity);

		c.setNestedThirdProperty(thirdEntity);

		a.setOtherEntity(c);

		//mapped associations
		SecondEntity x = factory.getInstance(SecondEntity.class);
		x.setName(rds());
		factory.persist(x);


		SecondEntity z = factory.getInstance(SecondEntity.class);
		z.setName(rds());



		ThirdEntity embedEntityInRecursiveMappedEntity = new ThirdEntity();
		embedEntityInRecursiveMappedEntity.setSomeProperty(rds());

		z.setEmbedEntityInRecursiveMappedEntity(embedEntityInRecursiveMappedEntity);
		x.setRecursiveMapped(z);
		factory.persist(z);
		factory.persist(x);

		a.setMappedEntity(x);

		factory.persist(a);


		//log.debug("created id is: " + a.getId());
		FirstEntity b = factory.get(FirstEntity.class, a.getId());
		assertNotNull(b);
		assertEquals(b.getId(), a.getId());
		assertEquals(b.getName(), a.getName());
		assertEquals(b.getDescription(), a.getDescription());
		assertEquals(b.getPhone(), a.getPhone());
		assertEquals(b.getScore(), a.getScore());
		assertEquals(b.getDate(), a.getDate());
		assertEquals(b.getOtherDate(), a.getOtherDate());
		assertEquals(b.getChangedColumnName(), a.getChangedColumnName());
		assertTrue(b.getOtherDate().before(a.getDate()));
		assertNotNull(b.getOtherEntity());
		assertNotNull(a.getOtherEntity());
		assertNotNull(a.getMappedEntity());
		assertNotNull(b.getMappedEntity());
		assertNotNull(a.getMappedEntity().getName());
		assertNotNull(b.getMappedEntity().getName());
		assertNotNull(a.getMappedEntity().getRecursiveMapped());
		assertNotNull(b.getMappedEntity().getRecursiveMapped());
		assertNotNull(a.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity());
		assertNotNull(b.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity());
		assertNotNull(a.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity().getSomeProperty());
		assertNotNull(b.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity().getSomeProperty());
		assertEquals(b.getMappedEntity().getName(), a.getMappedEntity().getName());
		assertNotNull(a.getOtherEntity().getNestedThirdProperty());
		assertNotNull(b.getOtherEntity().getNestedThirdProperty());
		assertNotNull(a.getOtherEntity().getNestedThirdProperty().getFourthEntity());
		assertNotNull(b.getOtherEntity().getNestedThirdProperty().getFourthEntity());
		assertNull(a.getOtherEntity().getNestedThirdProperty().getFourthEntityNull());
		assertNull(b.getOtherEntity().getNestedThirdProperty().getFourthEntityNull());
		assertNotNull(a.getOtherEntity());
		assertEquals(b.getOtherEntity(), a.getOtherEntity());
		assertEquals(b.getMappedEntity(), a.getMappedEntity());
		assertEquals(b.getMappedEntity().getRecursiveMapped(), a.getMappedEntity().getRecursiveMapped());
		assertEquals(b.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity(), a.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity());
		assertEquals(b.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity().getSomeProperty(), a.getMappedEntity().getRecursiveMapped().getEmbedEntityInRecursiveMappedEntity().getSomeProperty());
		assertEquals(b.getMappedEntity().getId(), x.getId());
		assertEquals(b.getOtherEntity().getNestedThirdProperty(), a.getOtherEntity().getNestedThirdProperty());
		assertEquals(b.getOtherEntity().getNestedThirdProperty().getFourthEntity(), a.getOtherEntity().getNestedThirdProperty().getFourthEntity());
		assertEquals(b, a);

		String newName = "nameChanged";
		double score = 99.0;
		factory.executeQuery(Integer.class,
				Query.get(update(
						columnFamily("FirstEntity"),
						set(
								assign("name", newName),
								assign("score", score)
						),
						where(
								keyIn(a.getId())
						)
				))
		);
		FirstEntity loadedAfterUpdate = factory.get(FirstEntity.class, a.getId());
		assertEquals(newName, loadedAfterUpdate.getName());
		assertEquals(score, loadedAfterUpdate.getScore());

		List<FirstEntity> results = factory.getResultList(FirstEntity.class, Query.get(select(
				allColumns(),
				from("FirstEntity")
		)));
		assertEquals(1, results.size());
	}


}
