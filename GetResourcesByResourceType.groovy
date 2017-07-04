//This script is licensed under:
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/
//
//This script gets all resources that are using a certain resource type under the given resource path.
//The output is a list of paths to the resources that are using the given resource type.
//
//Command line usage:
//groovy -cp udclient.jar GetResourcesByResourceType.groovy http(s)://hostname:port username password resourcetype resourcepath
//Ignore resourcepath or specify / for resourcepath if all resources are to be searched.
//
//Usage from an UrbanCode Deploy process does not require the server URL, username and password.
//Refer to the following wiki on how to call the script from an UrbanCode Deploy process:
//https://github.com/IBM-UrbanCode/groovy-sample-scripts-UCD/wiki/Launching-Groovy-scripts-that-invoke-the-REST-API-from-a-process-step
//
//Tested with IBM UrbanCode Deploy 6.2.4.1

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder;
import groovy.json.JsonSlurper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.System;
import java.net.URLEncoder

class Parameters{
	String serverURL;
	String username;
	String password;
	String resourceType;
	String resourcePath;
}

//sets the values of the Parameters class which contains the server url and the username/password
void initializeParameters(Parameters parameters){
	if (args.size() == 0 || args.size() == 3 || args.size() > 5) {
		printUsage();
		System.exit(1)
	}
	//When there are 4 or 5 parameters, we assume that we are running from command line
	else if (args.size()==4){
		parameters.serverURL = args[0];
		parameters.username = args[1];
		parameters.password = args[2];
		parameters.resourceType = args[3];
	}
	else if (args.size()==5){
		parameters.serverURL = args[0];
		parameters.username = args[1];
		parameters.password = args[2];
		parameters.resourceType = args[3];
		parameters.resourcePath = args[4];
	}
	//otherwise we assume that we are running from an UrbanCode Deploy process
	//we obtain the Server URL from a system property: 
	//https://www.ibm.com/support/knowledgecenter/SS4GSP_6.2.2/com.ibm.udeploy.doc/topics/propertiesreference.html
	//we obtain the Token from the environment variable DS_AUTH_TOKEN
	else {
		try {
			parameters.serverURL = "${p:server.url}";
			parameters.username="PasswordIsAuthToken";
			parameters.password=System.getenv("DS_AUTH_TOKEN");

			parameters.resourceType = args[0];
			
			if (args.size()==2){
				parameters.resourcePath = args[1];
			}
		}
		catch (Exception ex){
			org.codehaus.groovy.runtime.StackTraceUtils.sanitize(ex).printStackTrace();
			printUsage();
			System.exit(1)
		}
	}
}

void printUsage() { 
	println "Command Line usage:"
	println "groovy -cp udclient.jar GetResourcesByResourceType.groovy http(s)://hostname:port username password resourcetype resourcepath\n"
	println "Ignore resourcepath or specify / for resourcepath if all resources are to be searched."
}

//Initializes an HttpClient that accepts all certificates
//depends on import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder 
//included in HttpComponents-Util.jar, which is partly included in udclient.jar
HttpClient initializeClient(String username,String password){
	HttpClientBuilder builder = new HttpClientBuilder();
	builder.setPreemptiveAuthentication(true);
	builder.setUsername(username);
	builder.setPassword(password);
	//Accept all certificates
	builder.setTrustAllCerts(true);
	return builder.buildClient();
}

//Perform a given HttpRequest, assume answer is <= 299 , parse the outcome as JSON. Also release the connection for the Request.
Object performGetRequest(HttpClient client, String requestURL) {
	HttpRequest request = new HttpGet(requestURL);
	//Execute the REST GET call
	HttpResponse response = client.execute(request); 
	//Check that the call was successful
    int statusCode = response.getStatusLine().getStatusCode(); 
	if ( statusCode > 299 ) {
		println "ERROR : HttpGet to: "+requestURL+ " returned: " +statusCode;
		System.exit(1);
	} else {
		//Convert the InputStream returned by response.getEntity().getContent() to a String 
		BufferedReader reader=new BufferedReader(new InputStreamReader(response.getEntity().getContent(),"UTF-8"));
		StringBuilder builder=new StringBuilder();
		for(String line=null;(line=reader.readLine())!=null;){
			   builder.append(line).append("\n");
		}
		//Parse the returned JSON 
		//http://groovy-lang.org/json.html
		JsonSlurper slurper = new JsonSlurper();
		objects=slurper.parseText(builder.toString());
		//Ensure to release the connection
		request.releaseConnection();
		return objects;
	}
}

//Get all resources that are using the given resource type under the given resource path
boolean getResources(HttpClient client, String serverURL, String resourceType, String resourcePath) {
	String requestURL;
	boolean found = false;
	
	if (resourcePath == null || resourcePath.equals("/")) {
		requestURL = serverURL+"/cli/resource";
	} else {
		found = getResourceInfo(client, serverURL, resourceType, resourcePath) || found;
		
		requestURL = serverURL+"/cli/resource?parent="+URLEncoder.encode(resourcePath, "UTF-8");
	}
	
	resources=performGetRequest(client, requestURL);
	
	if (resources) { 
		//Recursively traverse resources under the current resource path
		resources.each{
			found = getResources(client, serverURL, resourceType, it.path) || found;			
		}
	} else if (resourcePath == null || resourcePath.equals("/")) {
		println "Getting top-level resource groups returned 0 record. Please check if the specified user has the permission to view resources."
		System.exit (1);
	}
	
	return found;
}

boolean getResourceInfo(HttpClient client, String serverURL, String resourceType, String resourcePath){
	boolean found = false;

	resourceInfo=performGetRequest(client, serverURL+"/cli/resource/info?resource="+URLEncoder.encode(resourcePath, "UTF-8"));

	if (resourceInfo) {
		def teams = resourceInfo.extendedSecurity.teams;			
			
		if (teams) {
			teams.find {
				if (resourceType.equals(it.resourceRoleLabel)) {
					println resourceInfo.path;
					
					found = true;
					
					return true;
				}				
				
				return false;		 
			}
		}
	}
	
	return found;
}

//Main script contents
Parameters parameters = new Parameters();
initializeParameters(parameters);
HttpClient client = initializeClient(parameters.username,parameters.password);

boolean found = getResources(client, parameters.serverURL, parameters.resourceType, parameters.resourcePath);

if (!found) {
	StringBuffer notFoundMsg = new StringBuffer("Found no resource using the resource type \"").append(parameters.resourceType).append("\"");
	if (parameters.resourcePath != null) { 
		notFoundMsg.append(" under the path \"").append(parameters.resourcePath).append("\"");
	}
	
	println notFoundMsg.toString();
}
