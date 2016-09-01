package net.guha.apps.rest.resources;

import net.guha.apps.rest.Utils;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IMolecule;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.smiles.smarts.SMARTSQueryTool;
import org.restlet.Context;
import org.restlet.data.*;
import org.restlet.resource.*;

/**
 * Perform substructure searches.
 * <p/>
 * The class supports matching a single klass against
 * a single target using a GET request. Also the service only
 * determines whether the target contains the klass, returning "true" if
 * present and "false" otherwise.
 * <p/>
 * If the target SMILES cannot be parsed, it returns "fail"
 * <p/>
 * If the SMARTS is invalid, a HTTP 500 is returned
 * <p/>
 * Using POST you can match multiple targets against a klass. In this
 * case the POST request should have two form elements called "target", whose value
 * should be a comma separated list of SMILES and "klass" which should be
 * a single SMARTS pattern. The return value is a plain/text document with
 * N lines for N input SMILES. Each line is either "true", "false" or 'fail"
 */
public class SubstructureSearchResource extends Resource {

    String target;
    String query;

    public SubstructureSearchResource(Context context, Request request, Response response) {
        super(context, request, response);
        target = (String) request.getAttributes().get("target");
        query = (String) request.getAttributes().get("query");

        if (target != null) target = Reference.decode(target);
        if (query != null) query = Reference.decode(query);

        getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    }

    @Override
    public Representation represent(Variant variant) throws ResourceException {
        Representation representation = null;
        String result;
        if (variant.getMediaType().equals(MediaType.TEXT_PLAIN)) {
            if (query == null || target == null) throw new ResourceException(new CDKException("No query or target specified?"));
            try {
                result = doMatch(Utils.getMolecule(target), query);
            } catch (CDKException e) {
                throw new ResourceException(e);
            }
            if (result.equals("fail")) getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            else getResponse().setStatus(Status.SUCCESS_OK);
            representation = new StringRepresentation(result, MediaType.TEXT_PLAIN);
        }
        return representation;
    }

    public boolean allowPost() {
        return true;
    }

    public void setModifiable(boolean b) {
        super.setModifiable(false);
    }

    // handle a POST request
    public void acceptRepresentation(Representation representation) throws ResourceException {
        if (representation.getMediaType().equals(MediaType.APPLICATION_WWW_FORM)) {
            Form form = new Form(representation);
            String query = form.getFirstValue("query");
            String targets = form.getFirstValue("target");
            if (query == null || targets == null)
                throw new ResourceException(new CDKException("No form elements specified"));
            String[] smiles = targets.split(",");
            String result;
            try {
                result = doMatch(smiles, query);
            } catch (CDKException e) {
                throw new ResourceException(e);
            }
            getResponse().setStatus(Status.SUCCESS_OK);
            getResponse().setEntity(new StringRepresentation(result, MediaType.TEXT_PLAIN));
        } else {
            getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        }
    }

    private String doMatch(IAtomContainer molecule, String query) throws CDKException {
        SMARTSQueryTool sqt = new SMARTSQueryTool("C");
        try {
            sqt.setSmarts(Reference.decode(query));
        } catch (CDKException e) {
            throw new CDKException("Invalid SMARTS string");
        }
        String result;
        if (sqt.matches(molecule)) result = "true";
        else result = "false";
        return result;
    }

    private String doMatch(String[] smiles, String query) throws CDKException {
        SmilesParser sp = new SmilesParser(DefaultChemObjectBuilder.getInstance());
        SMARTSQueryTool sqt = new SMARTSQueryTool("C");
        sqt.setSmarts(query);
        StringBuffer result = new StringBuffer();
        for (String s : smiles) {
            s = Reference.decode(s);
            try {
                IMolecule mol = sp.parseSmiles(s);
                if (sqt.matches(mol)) result.append("true\n");
                else result.append("false\n");
            } catch (InvalidSmilesException e) {
                result.append("fail\n");
            }
        }
        result.deleteCharAt(result.length() - 1);
        return result.toString();
    }
}