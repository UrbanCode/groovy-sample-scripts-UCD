//This script is licensed under:
//Apache License
//Version 2.0, January 2004
//http://www.apache.org/licenses/
//Tested with IBM UrbanCode Deploy 6.2.5.0
//The script requires at least UrbanCode Deploy 6.2.2 because it uses the APIs:
//https://www.ibm.com/support/knowledgecenter/SS4GSP_6.2.2/com.ibm.udeploy.api.doc/topics/rest_cli_applicationprocessrequest_count_get.html
//https://www.ibm.com/support/knowledgecenter/SS4GSP_6.2.2/com.ibm.udeploy.api.doc/topics/rest_cli_applicationprocessrequest_get.html
//This script counts the executions of distinct Application processes by date, and for each Application process it prints the name, the Application name, the Environment name. 
//Command line usage:
//groovy -cp udclient.jar ListApplicationProcesRequestsByProcess.groovy https://hostname:8443 username password "initialTimeStamp"
//where "initialTimeStamp" has the format: "yyyy-mm-dd hh:mm:ss" and indicates the starting time from when to retrieve the process executions
//Usage from a UrbanCode Deploy process does not require the first three parameters, but you can execute the following from a Shell step in order to have the udclient classes on the classpath:
//${p:agent/GROOVY_HOME}/bin/groovy -cp ${p:agent/AGENT_HOME}/opt/udclient/udclient.jar groovyScript.groovy "initialTimeStamp"
//Example output
/**
Total number of Application process requests: 32
Number of Chunks: 1 of size: 100
Application Process Requests issued after:2017-01-01 00:00:00

Counts:
Application: WebSphere Configuration - Example Application
Environment: PROD
Application process: Partial Apply
Date: 19/07/2017
Count: 8
Application: App1
Environment: TEST
Application process: Deploy
Date: 03/07/2017
Count: 9
**/


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
import java.sql.Timestamp;
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.Objects;

class Parameters{
	String serverURL;
	String username;
	String password;
	//format of the time stamp is: yyyy-mm-dd hh:mm:ss
	String initialTimeStamp="2016-12-31 00:00:00";
}

//only works on Java 1.7 and higher because it uses java.util.Objects to define the equals and hashCode methods
class ApplicationProcessRequest{
	String date;
	String application;
	String environment;
	String applicationProcess;
	@Override
    public boolean equals(Object o) {

        if (!(o instanceof ApplicationProcessRequest)) {
            return false;
        }
        ApplicationProcessRequest applicationProcessRequest = (ApplicationProcessRequest) o;
        return Objects.equals(date, applicationProcessRequest.date) &&
               Objects.equals(application, applicationProcessRequest.application) &&
			   Objects.equals(environment, applicationProcessRequest.environment) &&
			   Objects.equals(applicationProcess, applicationProcessRequest.applicationProcess) ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, application,environment, applicationProcess);
    }
	
	
}	


	//sets the values of the Parameters class which contains the server url and the username/password
	void initializeParameters(Parameters parameters){
		//if the user provided the input parameters, we're running on the command line
		if (args.size()==4){
			parameters.serverURL = args[0];
			parameters.username = args[1];
			parameters.password = args[2];
			parameters.initialTimeStamp=args[3];
		}
		//otherwise we assume that we are running from a UrbanCode Deploy process
		//we obtain the Server URL from a system property: 
		//https://www.ibm.com/support/knowledgecenter/SS4GSP_6.2.2/com.ibm.udeploy.doc/topics/propertiesreference.html
		//we obtain the Token from the environment variable DS_AUTH_TOKEN
		else if (args.size()==1){
			try{
			parameters.serverURL="${p:server.url}";
			parameters.username="PasswordIsAuthToken";
			parameters.password=System.getenv("DS_AUTH_TOKEN");
			parameters.initialTimeStamp=args[0];
			}//If we do not have the above variables, we might be running from the command line, but the user did not pass the expected parameters
			catch (Exception ex){
				println "Command Line usage: ListAllApplicationProcessRequestTrace ServerURL username password initialTimeStamp\nDo not provide - ServerURL username password - if running from a UrbanCode Deploy process"
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
	
    //returns a Hashtable that contains the application process request ids from a given start time in milliseconds (startedAfter) by breaking the request in chunks of chunkSize

	Hashtable <ApplicationProcessRequest, Integer> getApplicationProcessRequests(HttpClient client, String serverURL, long startedAfter, int chunkSize){
		
		//store the ApplicationProcessRequest object as the key, and the count as the value
	    def requestsPerDate = new Hashtable <ApplicationProcessRequest, Integer>();

		//count how many application process requests there are
	    def requestNumber = performGetRequest(client, serverURL+"/cli/applicationProcessRequest/count?"+startedAfter);
		println "Total number of Application process requests: "+requestNumber.appProcReqCount
        //prepare the GET call to the REST endpoint /cli/applicationProcessRequest?startedAfter=1471603999000&startIndex=200" 
		//perform the step in steps of chunkSize
		int segments = requestNumber.appProcReqCount/chunkSize;
		if ((segments*chunkSize)< requestNumber.appProcReqCount ) segments++;
		println "Number of Chunks: "+segments +" of size: "+chunkSize

		for(long i =0 ; i < segments; i++){
		    //startIndex is the current index i multiplied by chunkSize
			partialApplicationProcessRequests=performGetRequest(client, serverURL+"/cli/applicationProcessRequest?startedAfter="+startedAfter+"&startIndex="+i*chunkSize);
			partialApplicationProcessRequests.each{
			
				def request = new ApplicationProcessRequest();
			
				request.application = it.application.name;
				request.environment = it.environment.name;
				request.applicationProcess = it.applicationProcess.name;
				SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
				request.date = sdf.format(new Date(it.startTime))
	
				//If we found this same request before, increment the count
				if (requestsPerDate.containsKey(request)){
					requestsPerDate.put(request,requestsPerDate.get(request)+1);
				}//If we never found this request, add it to the Hashtable and set the count to 1
				else{
					requestsPerDate.put(request,1);
				}
				
				
			}	
		}
		return requestsPerDate;
	}
	//return the trace of a given application process request
	Object getApplicationProcessRequestInfo(HttpClient client, String serverURL, String applicationRequestId){

		//prepare GET request to https://localhost:8443//cli/applicationProcessRequest/info/{request}
		def applicationProcessRequestInfo = performGetRequest(client, serverURL+"/cli/applicationProcessRequest/info/"+applicationRequestId);
		return applicationProcessRequestInfo;
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
			//println builder.toString();
			//Parse the returned JSON 
			//http://groovy-lang.org/json.html
			JsonSlurper slurper = new JsonSlurper();
			objects=slurper.parseText(builder.toString());
			//Ensure to release the connection
			request.releaseConnection();
			return objects;
		}
	}
	
	String printDuration(long millis){
		def hours = TimeUnit.MILLISECONDS.toHours(millis)
		def minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - hours*60
		def seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - hours*60*60-minutes*60
		return hours+":"+minutes+":"+seconds
		
	}
	


	
	//Main script contents
	Parameters parameters = new Parameters();
	initializeParameters(parameters);
	HttpClient client = initializeClient(parameters.username,parameters.password);
	//do not change the chunk size, as the API will return segments of max size = 100
	int chunkSize=100;

	Timestamp t = Timestamp.valueOf(parameters.initialTimeStamp);
	long startedAfter = t.getTime();
	Hashtable<ApplicationProcessRequest, Integer> requestsPerDate = getApplicationProcessRequests(client,parameters.serverURL,startedAfter,chunkSize);
	println "Application Process Requests issued after:"+parameters.initialTimeStamp+"\n"
	
	println "Counts:"
	//Iterate over the keys of the Hashtable, which are the ApplicationPreocessRequests
	for (ApplicationProcessRequest request in requestsPerDate.keys())
	{
		println "Application: "+request.application;
		println "Environment: "+request.environment;
		println "Application process: "+request.applicationProcess;
		println "Date: "+request.date;
		println "Count: "+requestsPerDate.get(request);
						
	}
	
	