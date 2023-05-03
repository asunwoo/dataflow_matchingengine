package org.example.dataflow.externalapi;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.CreateDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.WriteDisposition;
import com.google.api.services.bigquery.model.TableRow;


public class ExternalApi {
    private static final Logger LOG = LoggerFactory.getLogger(VectorData.class);
    public static PCollection<VectorData> readFromQuery(Pipeline pipeline) {
        PCollection<VectorData> results =
        // BigQueryIO.TypedRead<TableRow> typedRows =
                pipeline
                        .apply(
                                "Read from BigQuery query",
                                BigQueryIO.readTableRows()
                                    .fromQuery(String.format(
                                            "SELECT vector FROM `foo.vectorbar`"))
                                    .usingStandardSql())
                    .apply(
                            "TableRows to MyData",
                            MapElements.into(TypeDescriptor.of(VectorData.class)).via(
                                VectorData::fromTableRow));
        return results;
    }

    static class MatchingEngineCall extends DoFn<VectorData, TableRow> {
        @ProcessElement
        public void processElement(@Element VectorData element, OutputReceiver<TableRow> receiver) {
            callMatchingEngine(element);
            receiver.output(element.getMatchedTableRow());
        }

        private static void callMatchingEngine(VectorData vectorData){
            //DO MATCHING ENGINEY STUFF HERE
            //TODO set a fixed length to the ArrayList if vectors are fixed or typically above a size
            //Just setting to same vector now, it should be replaced with the matchedVector from the call
            LOG.info("Starting Vector: " + vectorData.startingVector.toString());
            vectorData.matchedVector = new ArrayList<Double>(vectorData.startingVector);
            LOG.info("Matched Vector: " + vectorData.matchedVector.toString());
        }
    }

    static void runExternalApis(Pipeline p) {
        LOG.info("************Starting Pipeline************");
        PCollection<VectorData> rows = readFromQuery(p);
        LOG.info("************Calling Matching Engine************");
        PCollection<TableRow> entries = rows.apply(ParDo.of(new MatchingEngineCall()));
        LOG.info("************Inserting Results************");
        entries.apply("Write to BigQuery", BigQueryIO.writeTableRows()
            .to(String.format("%s:%s.%s", "red-road-356318", "foo", "matchedbar"))
            //.withSchema(schema)
            .withCreateDisposition(CreateDisposition.CREATE_NEVER)
            .withWriteDisposition(WriteDisposition.WRITE_TRUNCATE));
    }

    public static void main(String[] args) {
        for(int i=0; i<args.length; i++) {
            System.out.println(args[i]);
        }
        PipelineOptions options = PipelineOptionsFactory.fromArgs(args).create();
        Pipeline p = Pipeline.create(options);
        runExternalApis(p);
        p.run();

    }
}