package ul.sssr.loadbalancer;

import java.net.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.io.*;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2AsyncClient;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

/**
 * @author andy
 *
 */
/**
 * This Class ensures the Elastic Load Balancing for Workers making requests to Amazon SQS : 
 * "L'idée est d'avoir un serveur minimal qui reçoit toutes les requêtes HTTP et qui les dépose dans une queue de message SQS. 
 * En plus de ce travail, le serveur web est chargé de créer et supprimer des instances de travailleur de manière élastique. 
 * Quand la taille de la queue devient trop élevée il crée des instances, quand elle est trop petite, il en supprime.
 *  Il est aussi chargé de produire la réponse HTTP à renvoyer au client."
 * <p>
 * <b>Prerequisites:</b> You must have a valid Amazon Web
 * Services developer account, and be signed up to use Amazon SQS. For more
 * information on Amazon SQS, see http://aws.amazon.com/sqs.
 * <p>
 * Fill in your AWS access credentials in the provided credentials file
 * template, and be sure to move the file to the default location
 * (/home/andy/.aws/credentials) where the sample code will load the credentials from.
 * <p>
 * <b>WARNING:</b> To avoid accidental leakage of your credentials, DO NOT keep
 * the credentials file in your source directory.
 */
public class Server {

	static int portNumber = 8089;
	static AWSCredentials credentials;
	static String myQRequestUrl;
	static AmazonSQS sqs;
	static int k;
	static Queue<String> fifoSQS = new LinkedList<String>();

	public static void main(String[] args) throws IOException {

		/*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (/home/andy/.aws/credentials).
         */
		try {
			credentials = new ProfileCredentialsProvider().getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(
					"Cannot load the credentials from the credential profiles file. "
							+ "Please make sure that your credentials file is at the correct "
							+ "location (/home/andy/.aws/credentials), and is in valid format.",
					e);
		}

		sqs = new AmazonSQSClient(credentials);
		Region euCentral1 = Region.getRegion(Regions.EU_CENTRAL_1);
		sqs.setRegion(euCentral1);

		try {
			// 1) Create the  Queue semigambi-QRequest
			CreateQueueRequest createQueueRequest = new CreateQueueRequest("semigambi-QRequest");
			myQRequestUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
			
			//2) Purge the Queue request semigambi-QRequest
			PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest(myQRequestUrl);
			sqs.purgeQueue(purgeQueueRequest);
		} catch (AmazonServiceException ase) {
			System.out
					.println("Caught an AmazonServiceException, which means your request made it "
							+ "to Amazon SQS, but was rejected with an error response for some reason.");
			System.out.println("Error Message:    " + ase.getMessage());
			System.out.println("HTTP Status Code: " + ase.getStatusCode());
			System.out.println("AWS Error Code:   " + ase.getErrorCode());
			System.out.println("Error Type:       " + ase.getErrorType());
			System.out.println("Request ID:       " + ase.getRequestId());
		} catch (AmazonClientException ace) {
			System.out
					.println("Caught an AmazonClientException, which means the client encountered "
							+ "a serious internal problem while trying to communicate with SQS, such as not "
							+ "being able to access the network.");
			System.out.println("Error Message: " + ace.getMessage());
		}
		
		//3) Initialize the request counter "k" to 0.
		k = 0;
		ServerSocket socket;
		
		//listen to the port Number,and adding a worker to all 35 messages  
		boolean listening = true;
		try {
			socket = new ServerSocket(portNumber);
			int max = 70;
			while (listening) {
				new ServerThread(socket.accept()).start();
				GetQueueAttributesRequest queueAttributeRequest = new GetQueueAttributesRequest(myQRequestUrl)
						.withAttributeNames("ApproximateNumberOfMessages");
				Map<String, String> attributes = sqs.getQueueAttributes(queueAttributeRequest).getAttributes();
				
				// get the  number of messages appears on the queue
				int messagesQueue = Integer.parseInt(attributes.get("ApproximateNumberOfMessages"));
				if (messagesQueue > max) 
					fifoSQS.add(aWorker());
				stopWorker(messagesQueue);
			}
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static class ServerThread extends Thread {
		private Socket socket = null;

		public ServerThread(Socket socket) {
			super("ServerThread");
			this.socket = socket;
		}

		public void run() {
			//String inputLine, outputLine;
			InputStream ist;
			String theValue = "";
			try {
				ist = socket.getInputStream();
				BufferedReader bre = new BufferedReader(new InputStreamReader(ist));
				String requestSQS = bre.readLine();
				if (requestSQS != null) {
					String[] requestParameter = requestSQS.split(" ");
					String[] requestParameter1 = requestParameter[1].split("/");
					if (sqsValue(requestParameter1[1])) {
						theValue = requestParameter1[1];
						int numberOfSQSRequest = k;
						
						//4)Increments the request counter "k"
						k++;
						//5)Creates a queue "QResponse-k"
						CreateQueueRequest createQueueRequest = new CreateQueueRequest("semigambi-QResponse-" + numberOfSQSRequest);
						String myQResponseUrl = sqs.createQueue(createQueueRequest).getQueueUrl();
						//6)Puts in the "QRequest" queue a message "val k" with the value of the integer followed by the value of "k"
						sqs.sendMessage(new SendMessageRequest(myQRequestUrl,theValue + " " + numberOfSQSRequest));
						boolean end = true;
						while (end) {
							List<Message> SQSmsgs = sqs.receiveMessage(new ReceiveMessageRequest(myQResponseUrl).withMaxNumberOfMessages(1))
									.getMessages();
							if (SQSmsgs.size() > 0) {
								Message theMessage = SQSmsgs.get(0);
								String SQSdata = theMessage.getBody();
								PrintWriter out = new PrintWriter(socket.getOutputStream(), true);  
								out.println("the fibonacci's result of  "+ theValue + " is " + SQSdata+".");
								sqs.deleteQueue(myQResponseUrl);
								end = false;
							}
						}
					}
				}
				socket.close();
			} catch (IOException ioe) {
			
				ioe.printStackTrace();
			}

		}
	}

	public static boolean sqsValue(String SQScharacter) {
		boolean aValue = true;
		char[] sqstable = SQScharacter.toCharArray();
		for (char sqschar : sqstable) {
			if (!Character.isDigit(sqschar) && aValue) 
				aValue = false;
		}
		return aValue;
	}
	

	public static String aWorker() {
		AmazonEC2 ec2 = new AmazonEC2AsyncClient(credentials);
		ec2.setEndpoint("ec2.eu-central-1.amazonaws.com");

		// Starting Elastic load balancing--->EC2 instances are created	with all parameters required
		RunInstancesRequest rir= new RunInstancesRequest()
				.withInstanceType("t2.micro").withImageId("ami-f9619996")
				.withMinCount(1).withMaxCount(1)
				.withSecurityGroupIds("andy_SG_frankfurt")
				.withKeyName("andy-key-pair");
		CreateTagsRequest ctr = new CreateTagsRequest();
		String id = ec2.runInstances(rir).getReservation()
				.getInstances().get(0).getInstanceId();
		ctr.withResources(
				(id)).withTags(
				new Tag("Name", "Worker"));
		ec2.createTags(ctr);

		return id;
	}
	
	public static void updateWorker(int SQSampleMsg){
		AmazonEC2 ec2 = new AmazonEC2AsyncClient(credentials);
		ec2.setEndpoint("ec2.eu-central-1.amazonaws.com");
		//update message while closing
		int mpn = 0 ;
		if( mpn  < fifoSQS.size()){
			
		}
		System.out.println("updating..");
	}

	public static void stopWorker(int msgSQS){
		AmazonEC2 ec2 = new AmazonEC2AsyncClient(credentials);
		ec2.setEndpoint("ec2.eu-central-1.amazonaws.com");
		int numberOfWorkers = msgSQS/35;
		if (numberOfWorkers < fifoSQS.size() ){	    
		    List<String> ids = new ArrayList<String>();
		    ids.add(fifoSQS.peek());
		    try {
		        // End up instances(retrieve,remove the head of this queue)
		        TerminateInstancesRequest tr = new TerminateInstancesRequest(ids);
		        ec2.terminateInstances(tr);
		        fifoSQS.poll();
		    } catch (AmazonServiceException asex) {
		        // print out exceptions
		        System.out.println("Error terminating instances");
		        System.out.println("Caught Exception: " + asex.getMessage());
		        System.out.println("Reponse Status Code: " + asex.getStatusCode());
		        System.out.println("Error Code: " + asex.getErrorCode());
		        System.out.println("Request ID: " + asex.getRequestId());
		    }		    
		}
	}
}
