//This script is licensed under:
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/
//Tested with IBM UrbanCode Deploy 6.2.4
//Command line usage:
//groovy -cp udclient.jar ListApplicationprocessProperties.groovy https://hostname:8443 username password
//Usage from a UrbanCode Deploy process does not require any input parameters, but you can execute the following from a Shell step in order to have the udclient.jar classes on the classpath:
//${p:agent/GROOVY_HOME}/bin/groovy -cp ${p:agent/AGENT_HOME}/opt/udclient/udclient.jar ListApplicationprocessProperties.groovy 
//Description:
//This process lists all applications, all application processes, all names, labels, default values of application process properties.
//Example output:
//
//Applications:
//
//Application Name: a1
//    Process Name: install
//        Variable Name: Change_Number
//        Variable Label: Change Number
//        Variable Default Value: ###.###


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
import java.net.URLEncoder;
import org.apache.commons.lang.StringUtils;

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
		//Use proxy if defined
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
	//returns a JSON Array that contains a JSON Object for each Application
	Object getApplications(HttpClient client, String serverURL){
        //prepare the GET call to the REST endpoint /cli/application		
		applications=performGetRequest(client,serverURL+"/cli/application" )
		return applications;
	}
	
	//returns a JSON Array that contains a JSON Object listing the processes of the supplied Application
	Object getApplicationProcesses(HttpClient client, String serverURL, String applicationId){
        //prepare the GET call to the REST endpoint /cli/applicationProcess
		applicationProcesses=performGetRequest(client, serverURL+"/cli/applicationProcess?application="+applicationId);
		return applicationProcesses;
	}
	//returns a JSON Array that contains a JSON Object listing the properties of the supplied Application process
	Object getApplicationProcessProperties(HttpClient client, String serverURL, String applicationId, String processName){
        //prepare the GET call to the REST endpoint /cli/applicationProcess/unfilledProperties?
		applicationProcessProperties=performGetRequest(client, serverURL+"/cli/applicationProcess/unfilledProperties?application="+applicationId+"&processName="+URLEncoder.encode(processName));
		return applicationProcessProperties;
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
	
	//Main script contents
	Credentials credentials = new Credentials();
	initializeParameters(credentials);
	HttpClient client = initializeClient(credentials.username,credentials.password);
	def applications = getApplications(client,credentials.serverURL);
	println "Applications:\n"
	//Use a groovy closure with the implicit parameter "it"
	//http://groovy-lang.org/closures.html
	//http://docs.groovy-lang.org/latest/html/groovy-jdk/java/util/Collection.html#each%28groovy.lang.Closure%29
	if (applications){
		applications.each{
			println "Application Name: "+it.name;
			def applicationId = it.id;
			def applicationProcesses = getApplicationProcesses(client,credentials.serverURL,applicationId);
			if (applicationProcesses){
				applicationProcesses.each{
					def processName = it.name;
					println "    Process Name: "+processName
					def applicationProcessProperties = getApplicationProcessProperties(client,credentials.serverURL,applicationId,processName);
					if(applicationProcessProperties){
						applicationProcessProperties.each{

							println "        Variable Name: "+ it.name;
							println "        Variable Label: "+ it.label;
							println "        Variable Default Value: "+ it.value;
						}
					}
				}
			}
		}	
	}
