
//This script is licensed under:
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/
//Tested with IBM UrbanCode Deploy 6.2.7.2
//Command line usage:
//groovy -cp udclient.jar CreateGenericProcess.groovy https://hostname:8443 username password GenericProcessFileAbsolutePath
//where GenericProcessFileAbsolutePath is the absolute path of a file based on the template MyGenericProcess.json
//Example:
//groovy -cp udclient.jar CreateGenericProcess.groovy https://localhost:8443 admin admin C:\temp\MyGenericProcess.json


import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.StringEntity

import com.urbancode.commons.httpcomponentsutil.HttpClientBuilder;

class Credentials{
	String serverURL;
	String username;
	String password;
	String inputFileAbsPath;

}
//sets the values of the Credentials class which contains the server url and the username/password GenericProcessFileAbsolutePath
void initializeParameters(Credentials credentials){
	//if the user provided the input parameters, we're running on the command line
	if (args.size()==4){
		credentials.serverURL = args[0];
		credentials.username = args[1];
		credentials.password = args[2];
		credentials.inputFileAbsPath = args[3]
	}
	else{

		println "Command Line usage: CreateGenericProcess ServerURL username password GenericProcessFileAbsolutePath"

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
void performPutRequest(HttpClient client, String requestURL, String payload){
	HttpRequest putRequest = new HttpPut(requestURL);

	putRequest.setEntity(new StringEntity(payload));
	putRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
	//Execute the REST PUT call
	HttpResponse response = client.execute(putRequest);
	//Check that the call was successful
	int statusCode = response.getStatusLine().getStatusCode();
	if ( statusCode > 299 ) {
		println "ERROR : HttpPut to: "+requestURL+ " returned: " +statusCode;

	}
}

//Main script contents
Credentials credentials = new Credentials();
initializeParameters(credentials);
HttpClient client = initializeClient(credentials.username,credentials.password);
def inputFile = new File(credentials.inputFileAbsPath)
performPutRequest(client,credentials.getServerURL()+"/cli/process/create",inputFile.text)

