
//This script is licensed under:
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/
//Tested with IBM UrbanCode Deploy 6.2.7.2
//Command line usage:
//groovy -cp udclient.jar DeleteResource.groovy https://hostname:8443 username password resourcePath
//where resourcePath is the  path of the resource to delete
//Example:
//groovy -cp udclient.jar DeleteResource.groovy https://localhost:8443 admin admin /TopResource/MyFolder1/MyFolder2


import org.apache.commons.lang.StringUtils
import org.apache.http.HttpHeaders
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpDelete

import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder

class Credentials{
	String serverURL
	String username
	String password

}

class Parameters{
	String resourcePath
}

//sets the values of the Credentials class which contains the server url and the username/password 
void initializeParameters(Credentials credentials, Parameters parameters){
	//if the user provided the input parameters, we're running on the command line
	if (args.size()==4){
		credentials.serverURL = args[0]
		credentials.username = args[1]
		credentials.password = args[2]
		parameters.resourcePath = args[3]
	}
	else{

		println "Command Line usage: DeleteResource ServerURL username password resourcePath"

		System.exit(1)

	}

}
//Initializes an HttpClient that accepts all certificates
//depends on import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder
//included in HttpComponents-Util.jar, which is partly included in udclient.jar
HttpClient initializeClient(String username,String password){
	HttpClientBuilder builder = new HttpClientBuilder()
	builder.setPreemptiveAuthentication(true)
	builder.setUsername(username)
	builder.setPassword(password)
	//Accept all certificates
	builder.setTrustAllCerts(true)
	//use proxy if defined
	if (!StringUtils.isEmpty(System.getenv("PROXY_HOST")) &&
	StringUtils.isNumeric(System.getenv("PROXY_PORT")))
	{
		builder.setProxyHost(System.getenv("PROXY_HOST"))
		builder.setProxyPort(Integer.valueOf(System.getenv("PROXY_PORT")))
	}

	if (!StringUtils.isEmpty(System.getenv("PROXY_USERNAME")) &&
	!StringUtils.isEmpty(System.getenv("PROXY_PASSWORD")))
	{
		builder.setProxyUsername(System.getenv("PROXY_USERNAME"))
		builder.setProxyPassword(System.getenv("PROXY_PASSWORD"))
	}

	return builder.buildClient()
}



//Perform a given HttpRequest, assume answer is <= 299 , parse the outcome as JSON. Also release the connection for the Request.
void performDeleteRequest(HttpClient client, String requestURL){
	HttpRequest deleteRequest = new HttpDelete(requestURL)

	deleteRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
	//Execute the REST Delete call
	HttpResponse response = client.execute(deleteRequest)
	//Check that the call was successful
	int statusCode = response.getStatusLine().getStatusCode()
	if ( statusCode > 299 ) {
		println "ERROR : HttpDelete to: "+requestURL+ " returned: " +statusCode 
		println response.getStatusLine()
		println response.entity.content.getText()
	}
	else {
		println "SUCCESS : HttpDelete to: "+requestURL+ " returned: " +statusCode 
	}
}

//Main script contents
Credentials credentials = new Credentials()
Parameters parameters = new Parameters()
initializeParameters(credentials, parameters)
HttpClient client = initializeClient(credentials.username,credentials.password)
performDeleteRequest(client,credentials.getServerURL()+"/cli/resource/deleteResource?resource="+parameters.resourcePath)

