// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vdslib/container/parameters.h>
#include <cppunit/extensions/HelperMacros.h>

using document::DocumentTypeRepo;
using namespace vdslib;

class Parameters_Test : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(Parameters_Test);
    CPPUNIT_TEST(testParameters);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testParameters();
};

#ifndef FASTOS_NO_THREADS
CPPUNIT_TEST_SUITE_REGISTRATION(Parameters_Test);
#endif

void
Parameters_Test::testParameters()
{
    Parameters par;
    par.set("fast", "overture");
    par.set("overture", "yahoo");
    par.set("number", 6);
    par.set("int64_t", INT64_C(8589934590));
    par.set("double", 0.25);
    std::unique_ptr<document::ByteBuffer> buffer(par.serialize());

    buffer->flip();
    DocumentTypeRepo repo;
    Parameters par2(repo, *buffer);

    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("overture"), par2.get("fast"));
    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("yahoo"), par2.get("overture"));
    std::string stringDefault = "wayne corp";
    int numberDefault = 123;
    int64_t int64Default = 456;
    double doubleDefault = 0.5;
    CPPUNIT_ASSERT_EQUAL(6, par2.get("number", numberDefault));
    CPPUNIT_ASSERT_EQUAL(INT64_C(8589934590), par2.get("int64_t", int64Default));
    CPPUNIT_ASSERT_DOUBLES_EQUAL(0.25, par2.get("double", doubleDefault), 0.0001);

    CPPUNIT_ASSERT_EQUAL(stringDefault, par2.get("nonexistingstring", stringDefault));
    CPPUNIT_ASSERT_EQUAL(numberDefault, par2.get("nonexistingnumber", numberDefault));
    CPPUNIT_ASSERT_EQUAL(int64Default,   par2.get("nonexistinglong", int64Default));
    CPPUNIT_ASSERT_EQUAL(doubleDefault, par2.get("nonexistingdouble", doubleDefault));
}
