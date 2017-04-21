package scrubjay.datasource

//import scrubjay.combination.{InterpolationJoin, NaturalJoin}
//import scrubjay.transformation.{ExplodeContinuousRange, ExplodeDiscreteRange}

import com.roundeights.hasher.Implicits._
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.types.DataType
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonIgnoreProperties, JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.core.{JsonGenerator, JsonParser}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import org.apache.spark.sql.catalyst.parser.LegacyTypeStringParser
import scrubjay.transformation.ExplodeDiscreteRange

import scala.util.Try

@JsonIgnoreProperties(
  value = Array("valid") // not sure why this gets populated
)
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type"
)
@JsonSubTypes(Array(
  new Type(value = classOf[CSVDatasetID], name = "CSVDatasetID"),
  new Type(value = classOf[ExplodeDiscreteRange], name = "ExplodeDiscreteRange")
))
abstract class DatasetID(inChildren: DatasetID*) extends Serializable {
  val children: Seq[DatasetID] = inChildren

  def isValid: Boolean
  def realize: DataFrame
  def asOption: Option[DatasetID] = {
    if (isValid)
      Some(this)
    else
      None
  }
}

object DatasetID {

  /**
    * Serializer/Deserializer for Schema (Spark DataFrame StructType)
    */
  class SchemaSerializer extends JsonSerializer[Schema] {
    override def serialize(value: Schema, gen: JsonGenerator, serializers: SerializerProvider): Unit = {
      gen.writeRawValue(value.prettyJson)
    }
  }

  class SchemaDeserializer extends JsonDeserializer[Schema] {
    override def deserialize(p: JsonParser, ctxt: DeserializationContext): Schema = {
      val raw = p.readValueAsTree().toString
      Try(DataType.fromJson(raw)).getOrElse(LegacyTypeStringParser.parse(raw)) match {
        case t: Schema => t
        case _ => throw new RuntimeException(s"Failed parsing Schema: $raw")
      }
    }
  }

  val objectMapper: ObjectMapper with ScalaObjectMapper = {
    val structTypeModule: SimpleModule = new SimpleModule()
    structTypeModule.addSerializer(classOf[Schema], new SchemaSerializer())
    structTypeModule.addDeserializer(classOf[Schema], new SchemaDeserializer())

    val m = new ObjectMapper with ScalaObjectMapper
    m.registerModule(DefaultScalaModule)
    m.registerModule(structTypeModule)
    m
  }

  def toJsonString(dsID: DatasetID): String = {
    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(dsID)
  }
  def fromJsonString(json: String): DatasetID = {
    objectMapper.readValue[DatasetID](json, classOf[DatasetID])
  }
  def toHash(dsID: DatasetID): String = "h" + toJsonString(dsID).sha256.hex

  private def toNodeEdgeTuple(dsID: DatasetID, parentName: Option[String] = None): (Seq[String], Seq[String]) = {

    val hash: String = toHash(dsID)

    // Create string of columns Node X Flops X Time, etc
    val columnString = dsID.realize.schema.fieldNames.mkString(" X ")

    // Graph node
    val style = dsID match {

      // Combined data sources
      // case _: NaturalJoin => "style=filled, fillcolor=\"forestgreen\", label=\"NaturalJoin\\n" + columnString + "\""
      // case _: InterpolationJoin => "style=filled, fillcolor=\"lime\", label=\"InterpolationJoin\\n" + columnString + "\""

      // Transformed data sources
      // case _: ExplodeDiscreteRange => "style=filled, fillcolor=\"deepskyblue\", label=\"ExplodeDiscrete\\n" + columnString + "\""
      // case _: ExplodeContinuousRange => "style=filled, fillcolor=\"lightskyblue\", label=\"ExplodeContinuous\\n" + columnString + "\""

      // Original data sources
      case _: CSVDatasetID => "style=filled, fillcolor=\"darkorange\", label=\"CSV\\n" + columnString + "\""
      // case _: CassandraDataset => "style=filled, fillcolor=\"darkorange\", label=\"Cassandra\\n" + columnString + "\""
      // case _: LocalDataset => "style=filled, fillcolor=\"darkorange\", label=\"Local\\n" + columnString + "\""

      // Unknown
      case _  => "label='unknown'"
    }
    val node: String = hash + " [" + style + "]"

    // Graph edge
    val edge: Seq[String] = {
      if (parentName.isDefined)
        Seq(parentName.get + " -> " + hash + " [penwidth=2]")
      else
        Seq()
    }

    val (childNodes: Seq[String], childEdges: Seq[String]) = dsID.children
      .map(toNodeEdgeTuple(_, Some(hash)))
      .fold((Seq.empty, Seq.empty))((a, b) => (a._1 ++ b._1, a._2 ++ b._2))

    (node +: childNodes, edge ++ childEdges)
  }

  def toDotString(dsID: DatasetID): String = {

    val (nodes, edges) = toNodeEdgeTuple(dsID)

    val header = "digraph {"
    val nodeSection = nodes.map("\t" + _).mkString("\n")
    val edgeSection = edges.map("\t\t" + _).mkString("\n")
    val footer = "}"

    Seq(header, nodeSection, edgeSection, footer).mkString("\n")
  }

  protected def saveStringToFile(text: String, filename: String): Unit = {
    import java.io.{BufferedWriter, File, FileWriter}

    val file = new File(filename)
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(text)
    bw.close()
  }

  def saveToJson(dsID: DatasetID, filename: String): Unit = saveStringToFile(toJsonString(dsID), filename)
  def saveToDot(dsID: DatasetID, filename: String): Unit = saveStringToFile(toDotString(dsID), filename)
}
