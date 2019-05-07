# Quill Code Generator

This library is designed to give you a few options as to what kind of 
schema to generate from JDBC metadata for quill. You can choose to generate
either simple case classes or a combination of case classes and query schemas,
as well as whether they should written to one file, multiple files, or
just a list of strings (useful for streaming directly into a repl).
Inspired by the Slick code generator albeit a bit simpler,
the code generator is fairly customizeable and a couple of
ready-to-use implementations come out of the box. 

Currently the code generator is only available for JDBC databases but it will
be extended in the future for Cassandra as well as others.

You can import the Code Generator using maven:
````xml
<dependency>
  <groupId>io.getquill</groupId>
  <artifactId>quill-codegen-jdbc_2.11</artifactId>
  <version>3.1.1-SNAPSHOT</version>
</dependency>
```` 

Or using sbt:
````scala
libraryDependencies += "io.getquill" %% "quill-codegen-jdbc" % "3.1.1-SNAPSHOT"
````


## SimpleJdbcCodegen
 
This code generator is to generates simple case classes representing tables
in a database. It does not generate Quill `querySchema` objects. 
Create one or multiple CodeGeneratorConfig objects
and call the `.writeFiles` or `.writeStrings` methods
on the code generator and the rest happens automatically.

Given the following schema:
````sql
-- Using schema 'public'

create table public.Person (
  id int primary key auto_increment,
  first_name varchar(255),
  last_name varchar(255),
  age int not null
);

create table public.Address (
  person_fk int not null,
  street varchar(255),
  zip int
);
````


You can invoke the SimpleJdbcCodegen like so:

````scala
val gen = new SimpleJdbcCodegen(snakecaseConfig, "com.my.project") {
    override def nameParser = SnakeCaseNames
}
gen.writeFiles("src/main/scala/com/my/project")
````

You can parse column and table names using either the `SnakeCaseNames` or the and the `LiteralNames` parser
which are used with the respective Quill Naming Strategies. (For more complex naming rules, use the `ComposeableTraitsJdbcCodegen`
in order to generate `querySchema` object which can have fully customized column/table names).

The following case case classes will be generated
````scala
// src/main/scala/com/my/project/public/Person.scala
package com.my.project.public
  
case class Person(id: Int, firstName: Option[String], lastName: Option[String], age: Int)
````

````scala
// src/main/scala/com/my/project/public/Address.scala
package com.my.project.public
  
case class Address(personFk: Int, street: Option[String], zip: Option[Int])
````


If you wish to generate schemas with custom table or column names, you need to use the ComposeableTraitsGen
in order to generate your schemas.

## Composeable Traits Codegen

The `ComposeableTraitsJdbcCodegen` allows you to customize table/column names in entity case classes
and generates the necessary `querySchema` object in order to map them. Additionally, it generates a 
database-independent query schema trait which can be composed with a `Context` object of your choice.

Given the following schema:
````sql
create table public.Person (
  id int primary key auto_increment,
  first_name varchar(255),
  last_name varchar(255),
  age int not null
);

create table public.Address (
  person_fk int not null,
  street varchar(255),
  zip int
);
````

Here is a example of how you could use the `ComposeableTraitsJdbcCodegen` in order to replace the
`first_name` and `last_name` properties with `first` and `last`.

````scala
val gen = new ComposeableTraitsJdbcCodegen(snakecaseConfig,"com.my.project") {
  override def namingStrategy: EntityNamingStrategy =
    CustomStrategy(
      col => col.columnName.toLowerCase.replace("_name", "")
    )
}
gen.writeFiles("src/main/scala/com/my/project")
````
 

The following schema should be generated as a result.
````scala
package com.my.project.public

case class Address(person_fk: Int, street: Option[String], zip: Option[Int])
case class Person(id: Int, first: Option[String], last: Option[String], age: Int)

// Note that by default this is formatted as "${namespace}Extensions"
trait PublicExtensions[Idiom <: io.getquill.idiom.Idiom, Naming <: io.getquill.NamingStrategy] {
  this:io.getquill.context.Context[Idiom, Naming] =>

  object AddressDao {
      def query = quote {
          querySchema[Address](
            "PUBLIC.ADDRESS",
            _.person_fk -> "PERSON_FK",
            _.street -> "STREET",
            _.zip -> "ZIP"
          )
        }
    }

    object PersonDao {
      def query = quote {
          querySchema[Person](
            "PUBLIC.PERSON",
            _.id -> "ID",
            _.first -> "FIRST_NAME",
            _.last -> "LAST_NAME",
            _.age -> "AGE"
          )
        }
    }
}
````

Later when declaring your Quill database context you can compose the context with
the `PublicExtensions` like so:
````scala
object MyCustomContext extends SqlMirrorContext[H2Dialect, Literal](H2Dialect, Literal)
  with PublicExtensions[H2Dialect, Literal]
````


## Stereotyping
Frequently in corporate databases, the same kind of table is duplicated across multiple schemas, databases, etc...
for different business units. Typically, all the duplicates of the table will have almost the same columns
with only minor differences. Stereotyped code-generation aims to take the 'lowest common denominator' of all these schemas
in order to produce a case class that can be used across all of them.

Examine the following H2 DDL:
````sql
create table Alpha.Person (
  id int primary key auto_increment,
  first_name varchar(255),
  last_name varchar(255),
  age int not null,
  foo varchar(255),
  num_trinkets int,
  trinket_type varchar(255) not null
);

create table Bravo.Person (
  id int primary key auto_increment,
  first_name varchar(255),
  bar varchar(255),
  last_name varchar(255),
  age int not null,
  num_trinkets bigint not null,
  trinket_type int not null
);
````

 * Firstly, note that `Alpha.Person` and `Bravo.Person` have the exact same columns except for `foo` and `bar` respectively.
   If a common table definition `Person` is desired, these columns must be omitted.
 * Secondly, note that their columns `num_trinkets` and `trinket_type` have different types.
   If a common table definition `Person` is desired, these columns must be expanded to the widest
   datatype of the two which is this case `bigint` for `num_trinkets` and `varchar(255)` for `trinket_type`.  

Both of the above actions are automatically performed by the `ComposeableTraitsJdbcCodegen`
(and `SimpleJdbcCodegen`) automatically when multiple tables with the same name are detected or if you
rename them using a custom `namingStrategy` causing this to happen. 
Here is an example of how that is done:

````scala
val gen = new ComposeableTraitsJdbcCodegen(twoSchemaConfig, "com.my.project") {
  override def namingStrategy: EntityNamingStrategy = CustomStrategy()
  override val namespacer: Namespacer =
    ts => if (ts.tableSchem.toLowerCase == "alpha" || ts.tableSchem.toLowerCase == "bravo") "common" else ts.tableSchem.toLowerCase
    
  // Be sure to set the querySchemaNaming correctly so that the different
  // querySchemas generated won't all be called '.query' in the common object (which would
  // case an un-compile-able schema to be generated).
  override def querySchemaNaming: QuerySchemaNaming = `[namespace][Table]`
}

gen.writeFiles("src/main/scala/com/my/project")
````

The following will then be generated. Note how `numTrinkets` is a `Long` (i.e. an SQL `bigint`) type and `trinketType` is a `String`
(i.e. an SQL varchar),

````scala
package com.my.project.common
  
case class Person(id: Int, firstName: Option[String], lastName: Option[String], age: Int, numTrinkets: Option[Long], trinketType: String)
  
trait CommonExtensions[Idiom <: io.getquill.idiom.Idiom, Naming <: io.getquill.NamingStrategy] {
  this:io.getquill.context.Context[Idiom, Naming] =>
  
  object PersonDao {
      def alphaPerson = quote {
          querySchema[Person](
            "ALPHA.PERSON",
            _.id -> "ID",
            _.firstName -> "FIRST_NAME",
            _.lastName -> "LAST_NAME",
            _.age -> "AGE",
            _.numTrinkets -> "NUM_TRINKETS",
            _.trinketType -> "TRINKET_TYPE"
          )
        }
  
      def bravoPerson = quote {
          querySchema[Person](
            "BRAVO.PERSON",
            _.id -> "ID",
            _.firstName -> "FIRST_NAME",
            _.lastName -> "LAST_NAME",
            _.age -> "AGE",
            _.numTrinkets -> "NUM_TRINKETS",
            _.trinketType -> "TRINKET_TYPE"
          )
        }
    }
}
 
````

Later when declaring your quill database context you can compose the context with
the `CommonExtensions` like so:
````scala
object MyCustomContext extends SqlMirrorContext[H2Dialect, Literal](H2Dialect, Literal)
  with CommonExtensions[H2Dialect, Literal]
````