//Tested with IBM UrbanCode Deploy 6.2.3.1
//Command line usage:
//groovy -cp udclient.jar ListAllApplications.groovy https://hostname:8443 username password
//Usage from a UrbanCode Deploy process does not require any input parameters, but you can execute the following from a Shell step in order to have the Apache HttpCore classes on the classpath:
//${p:agent/GROOVY_HOME}${p:agent/sys.file.separator}bin${p:agent/sys.file.separator}groovy -cp ${p:agent/AGENT_HOME}${p:agent/sys.file.separator}opt${p:agent/sys.file.separator}udclient${p:agent/sys.file.separator}udclient.jar ListAllApplications.groovy 
//Example output
//Applications:
//
//Name: App1
//Description: This is the description of App1
//Tags:
//      Tag1
//
//Name: App2
//Description:
//Tags:
//      Tag2
//      Tag1

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
import org.apache.http.impl.client.HttpClientBuilder;
import groovy.json.JsonSlurper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.System;

class Credentials{
	String serverURL;
	String username;
	String password;
	
}
	//sets the values of the Credentials class which contains the server url and the username/password
	void initializeParameters(Credentials credentials){
		//if the user provided the input parameters, we're running on the command line
		if (args.size()==3){
			credentials.serverURL = args[0];
			credentials.username = args[1];
			credentials.password = args[2];
		}
		//otherwise we assume that we are running from a UrbanCode Deploy process
		//we obtain the Server URL from a system property: 
		//https://www.ibm.com/support/knowledgecenter/SS4GSP_6.2.2/com.ibm.udeploy.doc/topics/propertiesreference.html
		//we obtain the Token from the environment variable DS_AUTH_TOKEN
		else{
			try{
			credentials.serverURL="${p:server.url}";
			credentials.username="PasswordIsAuthToken";
			credentials.password=System.getenv("DS_AUTH_TOKEN");
			}//If we do not have the above variables, we might be running from the command line, but the user did not pass the expected parameters
			catch (Exception ex){
				println "Command Line usage: ListAllApplications ServerURL username password\nDo not provide any parameters if running from a UrbanCode Deploy process"
				org.codehaus.groovy.runtime.StackTraceUtils.sanitize(ex).printStackTrace();
				System.exit(1)
			}
		}
		
	}
    //Initializes an HttpClient that accepts self-signed certificates
	HttpClient initializeClient(String username,String password){
		//Accept self-signed certificates
		SSLContextBuilder builder=new SSLContextBuilder();
		builder.loadTrustMaterial(null,new TrustSelfSignedStrategy());
		SSLConnectionSocketFactory sslsf=new SSLConnectionSocketFactory(builder.build());
		//Do basic Authentication
		CredentialsProvider provider=new BasicCredentialsProvider();
		UsernamePasswordCredentials credentials=new UsernamePasswordCredentials(username,password);
		provider.setCredentials(AuthScope.ANY,credentials);
		HttpClient client=HttpClientBuilder.create().setSSLSocketFactory(sslsf).setDefaultCredentialsProvider(provider).build();
		return client;
	}
	//returns a JSON Array that contains a JSON Object for each Application
	Object getApplications(client, String serverURL){
        //prepare the GET call to the REST endpoint /cli/application
		HttpRequest request = new HttpGet(serverURL+"/cli/application");
		//Execute the REST call
		HttpResponse response = client.execute(request); 
		//Check that the call was successful
        int statusCode = response.getStatusLine().getStatusCode(); 
        assert (statusCode == HttpStatus.SC_OK); 
        //Convert the InputStream returned by response.getEntity().getContent() to a String 
		BufferedReader reader=new BufferedReader(new InputStreamReader(response.getEntity().getContent(),"UTF-8"));
        StringBuilder builder=new StringBuilder();
        for(String line=null;(line=reader.readLine())!=null;){
               builder.append(line).append("\n");
		}
		//Parse the returned JSON 
		//http://groovy-lang.org/json.html
		JsonSlurper slurper = new JsonSlurper();
		applications=slurper.parseText(builder.toString());
		//Ensure to release the connection
		request.releaseConnection();
		return applications;
	}
	
	//Main script contents
	Credentials credentials = new Credentials();
	initializeParameters(credentials);
	HttpClient client = initializeClient(credentials.username,credentials.password);
	def applications = getApplications(client,credentials.serverURL);
	println "Applications:\n"
	//Use a groovy closure with the implicit parameter "it"
	//http://groovy-lang.org/closures.html
	//http://docs.groovy-lang.org/latest/html/groovy-jdk/java/util/Collection.html#each%28groovy.lang.Closure%29
	applications.each{
		println "Name: "+it.name;
		println "Description: "+it.description;
		println "Tags: ";
		it.tags.each{
			println "      "+it.name;
		}
		println ""
	}
	
	