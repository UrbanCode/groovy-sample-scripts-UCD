//This script is licensed under:
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/
//Tested with IBM UrbanCode Deploy 6.2.6.1
//Command line usage:
//groovy -cp udclient.jar AddVerionsToComponents.groovy https://hostname:8443 username password ApplicationName numberOfVersions
//where ApplicationName is the name of the application.
//The script will add numberOfVersions Versions to each Component referenced by this application
//Example:
//groovy -cp udclient.jar AddVerionsToComponents.groovy https://localhost:8443 admin admin MyapplicationName 5


import org.apache.commons.lang.StringUtils
import org.apache.http.HttpHeaders
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.StringEntity

import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder;

import groovy.json.JsonSlurper

class Credentials{
	String serverURL;
	String username;
	String password;
	String appName;
	int versionNumber;

}
//sets the values of the Credentials class which contains the server url and the username/password
void initializeParameters(Credentials credentials){
	//if the user provided the input parameters, we're running on the command line
	if (args.size() == 5){
		credentials.serverURL = args[0]
		credentials.username = args[1]
		credentials.password = args[2]
		credentials.appName = args[3]
		credentials.versionNumber = Integer.parseInt(args[4])
	}
	else{

		println "Command Line usage: AddVersionsToComponents ServerURL username password ApplicationName numberOfVersions"

		System.exit(1)

	}

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
	//use proxy if defined
	if (!StringUtils.isEmpty(System.getenv("PROXY_HOST")) &&
	StringUtils.isNumeric(System.getenv("PROXY_PORT")))
	{
		builder.setProxyHost(System.getenv("PROXY_HOST"));
		builder.setProxyPort(Integer.valueOf(System.getenv("PROXY_PORT")));
	}

	if (!StringUtils.isEmpty(System.getenv("PROXY_USERNAME")) &&
	!StringUtils.isEmpty(System.getenv("PROXY_PASSWORD")))
	{
		builder.setProxyUsername(System.getenv("PROXY_USERNAME"));
		builder.setProxyPassword(System.getenv("PROXY_PASSWORD"));
	}

	return builder.buildClient();
}

//Perform a given HttpRequest, assume answer is <= 299 , parse the outcome as JSON. Also release the connection for the Request.
Object performGetRequest(HttpClient client, String requestURL){
	HttpRequest request = new HttpGet(requestURL);
	//Execute the REST GET call
	HttpResponse response = client.execute(request);
	//Check that the call was successful
	int statusCode = response.getStatusLine().getStatusCode();
	if ( statusCode > 299 ) {
		println "ERROR : HttpGet to: "+requestURL+ " returned: " +statusCode;
		return null;
	}
	else{
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

//returns a JSON Array that contains a JSON Object for each Component
Object getComponents(HttpClient client, String serverURL){
	//prepare the GET call to the REST endpoint /cli/component
	components=performGetRequest(client,serverURL+"/cli/component" )
	return components;
}

//returns a JSON Array that contains a JSON Object for each Component
Object getComponent(HttpClient client, String serverURL, String componentId){
	//prepare the GET call to the REST endpoint /cli/component/info
	component=performGetRequest(client,serverURL+"/cli/component/info?component="+componentId )
	return component;
}

//returns a JSON Array that contains a JSON Object for each version of a given Component
Object getVersions(HttpClient client, String serverURL, String componentID){
	//prepare the GET call to the REST endpoint /cli/component/versions
	versions=performGetRequest(client,serverURL+"/cli/component/versions?component="+componentID )
	return versions;
}

Object getComponentsForApplication(client, serverURL, String appName){
	componentsForApplication = new ArrayList();
	components = getComponents(client, serverURL)
	components.each{
		componentId = it.id
		componentName = it.name
		component = getComponent(client, serverURL, componentId)
		applications = component.applications
		applications.each {
			if(it.name.equals(appName)) {
				componentsForApplication.add(componentId)
				println "Found Component name: "+componentName
			}
		}
	}
	return componentsForApplication
}

void addVersionsToComponent(HttpClient client, String serverURL, String componentID, ArrayList versionNames) {
	versionNames.forEach {
		performPostRequest(client,serverURL+"/cli/version/createVersion?component="+componentID+"&name="+it)
	}
}

//Perform a given HttpRequest, assume answer is <= 299 , parse the outcome as JSON. Also release the connection for the Request.
Object performPutRequest(HttpClient client, String requestURL, String payload){
	HttpRequest putRequest = new HttpPut(requestURL);

	putRequest.setEntity(new StringEntity(payload));
	putRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
	//Execute the REST PUT call
	HttpResponse response = client.execute(putRequest);
	//Check that the call was successful
	int statusCode = response.getStatusLine().getStatusCode();
	if ( statusCode > 299 ) {
		println "ERROR : HttpPut to: "+requestURL+ " returned: " +statusCode;

	}else {
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
		putRequest.releaseConnection();
		return objects;

	}
}

//Perform a given HttpRequest, assume answer is <= 299. Also release the connection for the Request.
Object performPostRequest(HttpClient client, String requestURL){
	HttpRequest postRequest = new HttpPost(requestURL);
	postRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
	//Execute the REST POST call
	HttpResponse response = client.execute(postRequest);
	//Check that the call was successful
	int statusCode = response.getStatusLine().getStatusCode();
	if ( statusCode > 299 ) {
		println "ERROR : HttpPost to: "+requestURL+ " returned: " +statusCode;

	}
	else {
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
		postRequest.releaseConnection();
		return objects;

	}
}

//generate version names which have an initial integer prefix followed by a unique id
ArrayList generateVersionNames(int versionNumber) {
	ArrayList names = new ArrayList()
	for(int i =0; i < versionNumber; i++)
	{
		UUID uuid = UUID.randomUUID();
		String randomUUIDString = uuid.toString();

		names.add(i +"_"+ randomUUIDString);
	}
	return names
}

//Main script contents
Credentials credentials = new Credentials();
initializeParameters(credentials);
HttpClient client = initializeClient(credentials.username,credentials.password);
//Loop over components and find if they belong to the wanted application
componentIds = getComponentsForApplication(client,credentials.serverURL, credentials.appName)

componentIds.each {
	componentId = it
	versionNames = generateVersionNames(credentials.versionNumber)
	addVersionsToComponent(client, credentials.serverURL, componentId, versionNames)
}



