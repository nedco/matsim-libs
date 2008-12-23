package playground.wrashid.tryouts.performance;

import java.util.ArrayList;
import java.util.LinkedList;

public class Lists {
	public static void main(String[] args) {
		

		// new ArrayList<Integer>(0): 8250 ms
		// new ArrayList<Integer>(20000000): 4422 ms
		//testArrayListAdd();
		
		// 17814 ms
		//testLinkedListAdd();
		
		// -----------------------------------------------
		
		// 204 ms
		//testArrayListGet (testArrayListAdd());
		
		// infinity...
		//testLinkedListGet (testLinkedListAdd());

		//-----------------------------------------------
		
		// new ArrayList<Integer>(0): 2734 ms
		// new ArrayList<Integer>(20000000): 2703 ms
		//testArrayListAddAll(testArrayListAdd());

		// infinity... / OutOfMemory
		//testLinkedListAddAll (testLinkedListAdd());
		
		
		// -----------------------------------------------
		
		// infinity
		//testArrayListPoll(testArrayListAdd());
		
		// list.size()>0: 1266 ms
		// !list.isEmpty(): 1438 ms
		testLinkedListPoll(testLinkedListAdd());
		
		// -----------------------------------------------
		
	}

	public static ArrayList<Integer> testArrayListAdd() {
		long time = System.currentTimeMillis();
		ArrayList<Integer> list = new ArrayList<Integer>(20000000);

		for (int i = 0; i < 20000000; i++) {
			list.add(i);
		}
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
		return list;
	}
	
	public static LinkedList<Integer> testLinkedListAdd() {
		long time = System.currentTimeMillis();
		LinkedList<Integer> list = new LinkedList<Integer>();

		for (int i = 0; i < 20000000; i++) {
			list.add(i);
		}
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
		return list;
	}
	
	public static void testArrayListGet(ArrayList<Integer> list){
		long time = System.currentTimeMillis();
		for (int i=0;i<list.size();i++){
			list.get(i);
		}
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
	}
	
	public static void testLinkedListGet(LinkedList<Integer> list){
		long time = System.currentTimeMillis();
		for (int i=0;i<list.size();i++){
			list.get(i);
		}
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
	}
	
	public static void testArrayListAddAll(ArrayList<Integer> list){
		long time = System.currentTimeMillis();
		ArrayList<Integer> list1 = new ArrayList<Integer>(20000000);
		list1.addAll(list);
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
	}
	
	public static void testLinkedListAddAll(LinkedList<Integer> list){
		long time = System.currentTimeMillis();
		LinkedList<Integer> list1 = new LinkedList<Integer>();
		list1.addAll(list);
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
	}
	
	public static void testArrayListPoll(ArrayList<Integer> list){
		long time = System.currentTimeMillis();
		while (list.size()>0){
			list.remove(0);
		}
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
	}
	
	public static void testLinkedListPoll(LinkedList<Integer> list){
		long time = System.currentTimeMillis();
		while (list.size()>0){
			list.poll();
		}
		System.out.println("time [ms]: " + (System.currentTimeMillis() - time));
	}

}
