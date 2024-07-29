package WizardTD.gameEnv;
// Imports
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.System;

// Static imports
import java.lang.Math;

// Error imports
import java.lang.IllegalArgumentException;

/**
 * Datastructure that facilitates spatial partioning by recursively subdividing areas into quarters
 */
public class Quadtree<Obj>{
    // Config vars
    private Vector2 defaultMinimumLeafSize = new Vector2(4,4); // The minimum size of the leaves; takes priority over other criteria for division
    private int defaultMaximumDepth = 5;
    private int defaultMaxChilds = 2;
    // Static methods
    /**
     * Computes the geohash given the node's depth, the parent's node and the quadrant it in
     * <p> each geohash digit is 2 bits and each successive depth appends that geohash digit onto the parents' so if a parent has a geohash of 3 (11), the next node would be something like 0111 (7) etc
     * @param parentGeohash
     * @param quadrant
     * @param Depth
     * @return
     */
    public static long computeGeohash(long parentGeohash, Leaf quadrant, int Depth){
        long returnValue = parentGeohash;
        int geohashPrefix = 0; // If only I could use a 2 bit number
        // Based on the quadrant, prepare a prefix for the parentGeohash
        switch(quadrant){
            case NE: // 10
                geohashPrefix = 2; break;
            case NW: // 11
                geohashPrefix = 3; break;
            case SE: // 00
                geohashPrefix = 0; break;
            case SW: // 01
                geohashPrefix = 1; break;}
        // Bitshift our prefix by Depth
        returnValue = geohashPrefix << ((Depth-1) << 1); //Normally we'd do depth * 2 since each geohash is 2 bits each but turns out we can also bitshift that too
        return returnValue;
    }
    private static Leaf getLeafQuadrant(float nodePosX, float nodePosY,float pointX, float pointY){    // And an overload where we just plug raw numbers in
            float dirX, dirY;
            dirX = pointX - nodePosX;
            dirY = pointY - nodePosY;
            // We've seen this pattern already
            if(dirX >= 0 && dirY >= 0){
                return Leaf.NE;
            }else if(dirX < 0 && dirY >= 0){
                return Leaf.NW;
            }else if(dirX >= 0 && dirY < 0){
                return Leaf.SE;
            } else {
                return Leaf.SW;
            }
    }
    // Enum declaration
    protected enum Leaf{NE, NW, SE, SW}
    // Helper methods
    private QuadtreeNode getFirstNode(Vector2 objectSize, Vector2 objectPosition){
        // returns the first node that's either a leaf or can't fit the (implied) object with the given size at the given position
        // ObjectSize is a square centered on ObjectPosition. So the corners of the bounds can be derived by doing Position +- Size/2
        //  Like lua code we first need to do a depth first search
        if(this.root == null){return null;}
        // So it turns out I can't write nested functions in java so there goes like half my implementation
        //  So I guess we can just do it iteratively
        LinkedList<QuadtreeNode> processStack = new LinkedList<QuadtreeNode>();
        QuadtreeNode returnValue = null;
        processStack.push(this.root);
        while(!processStack.isEmpty()){
            QuadtreeNode currentNode = processStack.pop();
            if(currentNode.isLeaf()){returnValue = currentNode; break;}
            // Get the direction vector between the ObjectPosition and the node's position
            //System.out.printf("%1$s, %2$s\n", objectPosition, currentNode);
            Vector2 directionVector = Vector2.subtract(objectPosition,currentNode.getPosition());
            // Based on the direction vector components, target a specific leaf
            QuadtreeNode childNode = null;
            if(directionVector.getX() >= 0 && directionVector.getY() >= 0){// NE
                childNode = currentNode.getNodeFromEnum(Leaf.NE);
            }else if(directionVector.getX() >= 0 && directionVector.getY() < 0){ // SE
                childNode = currentNode.getNodeFromEnum(Leaf.SE);
            }else if(directionVector.getX() < 0 && directionVector.getY() >= 0){ // NW
                childNode = currentNode.getNodeFromEnum(Leaf.NW);
            }else { // Or if x < 0 and y < 0, SW
                childNode = currentNode.getNodeFromEnum(Leaf.SW);
            }
            // if the childNode is smaller than the size of the object then we stop since we've found the smallest node that can fit the object with the size at the position
            Vector2 childSize = childNode.getSize();
            if(objectSize.getX() > childSize.getX() || objectSize.getY() > childSize.getY()){returnValue = currentNode; break;}
            // Then get the direction vector between the position and the child node
            Vector2 childItemDirVec = Vector2.subtract(childNode.getPosition(), objectPosition);
            // And use that to find out if the object with the given size at the given position overlaps with other nodes
            if((Math.abs(childItemDirVec.getX()) < ((childSize.getX() + objectSize.getX())/2)) && (Math.abs(childItemDirVec.getY()) < ((childSize.getY() + objectSize.getY())/2)) ){returnValue = currentNode; break;}
            // If its not either of those two then enqueue the childNode
            processStack.push(childNode);
        }
        return returnValue;
    }
    private boolean intersectsWith(Vector2 size, Vector2 position, QuadtreeNode node){
        // Returns true if the item with the given size and position intersect with the given node
        Vector2 nodePos, nodeSize; nodePos = node.getPosition(); nodeSize = node.getSize();
        float dirX, dirY; dirX = nodePos.getX() - position.getX(); dirY = nodePos.getY() - position.getY();
        // if the absolute value of the direction vector is greater than half the size of the object plus half the size of the node then its not intersecting
        if((Math.abs(dirX) >= ((nodeSize.getX() + size.getX())/2)) || (Math.abs(dirY) >= ((nodeSize.getY() + size.getY())/2))){return false;}
        return true;
    }
    private QuadtreeNode getNodeAtPosition(Vector2 objectPosition){
        // We do the same thing as most implementations where we just grab the direction vector
        LinkedList<QuadtreeNode> processStack = new LinkedList<QuadtreeNode>();
        processStack.push(this.root);
        while(!processStack.isEmpty()){
            QuadtreeNode currentNode = processStack.pop();
            // If the currentNode is a leaf then return that since we've traversed the entire tree to the closest leaf to the point
            if(currentNode.isLeaf()){return currentNode;}
            Vector2 currentNodePos = currentNode.getPosition();
            Leaf selectedQuadrant = getLeafQuadrant(currentNodePos.getX(), currentNodePos.getY(), objectPosition.getX(), objectPosition.getY());
            processStack.push(currentNode.getNodeFromEnum(selectedQuadrant));
        }
        return null;
    }
    private void moveObjectsToLeaves(QuadtreeNode targetNode){
        // Since we've specified that removing takes place at the top level since we have to remove objects from multiple nodes, this is where we shuffle objects down
        // So for every object in the targetNode's objects list
        ArrayList<QuadtreeObjectContainer<Obj>> nodeObjects = targetNode.getObjects();
        for(int i =0; i<nodeObjects.size();i++){
            QuadtreeObjectContainer<Obj> currentContainer = nodeObjects.get(i);
            // Remove the reference to this node from the container's object 
            ArrayList<QuadtreeNode> containerNodes = currentContainer.getQuadtreeNodes();
            for(int l = 0; l < containerNodes.size(); l++){
                if(containerNodes.get(l) == targetNode){
                    containerNodes.remove(l);
                    break;
                }
            }            
            //Since this is mainly for nodes that have been split, we can assume that each subnode is a leaf
		    //  So we start with the direction vector from the child to the Node.Position
		    //  And check which quadrant its closer to
            QuadtreeNode NELeaf = targetNode.getNodeFromEnum(Leaf.NE); if(intersectsWith(currentContainer.getSize(), currentContainer.getPosition(), NELeaf)){
                NELeaf.addObject(currentContainer);
            }
            QuadtreeNode NWLeaf = targetNode.getNodeFromEnum(Leaf.NW); if(intersectsWith(currentContainer.getSize(), currentContainer.getPosition(), NWLeaf)){
                NWLeaf.addObject(currentContainer);
            }
            QuadtreeNode SELeaf = targetNode.getNodeFromEnum(Leaf.SE); if(intersectsWith(currentContainer.getSize(), currentContainer.getPosition(), SELeaf)){
                SELeaf.addObject(currentContainer);
            }
            QuadtreeNode SWLeaf = targetNode.getNodeFromEnum(Leaf.SW); if(intersectsWith(currentContainer.getSize(), currentContainer.getPosition(), SWLeaf)){
                SWLeaf.addObject(currentContainer);
            }
        
        }
    }    
    private ArrayList<QuadtreeNode> getNodesInArea(Vector2 position, Vector2 size){
        /*LinkedList<QuadtreeNode> processQueue = new LinkedList<QuadtreeNode>(); // Used for phase 2 where we do a breadth first search to find all overlapping nodes
        LinkedList<QuadtreeNode> processStack = new LinkedList<QuadtreeNode>(); // Used in phase 1 to find the first node that can't fit the area
        while(!processStack.isEmpty()){
            QuadtreeNode currentNode = processStack.pop();
            // If the node is a leaf then break since we've already found the first node that can fit the object whether the tree wants to or not
            if(currentNode.isLeaf()){processQueue.add(currentNode); break;}
            Vector2 currentNodePos = currentNode.getPosition();
            Leaf quadrant = getLeafQuadrant(currentNodePos.getX(), currentNodePos.getY(), position.getX(), position.getY());
            QuadtreeNode leafAtQuadrant = currentNode.getNodeFromEnum(quadrant);
            // If the child node is smaller than the size then we've found the last node that fits the object
            if(leafAtQuadrant.getSize().getX() < size.getX() || leafAtQuadrant.getSize().getY() < size.getY()){processQueue.add(currentNode); break;}
            // Calculate the direction vector between the leafAtQuadrant and position
            float leafVectorX, leafVectorY;
            leafVectorX = position.getX() - leafAtQuadrant.getPosition().getX(); leafVectorY = position.getY() - leafAtQuadrant.getPosition().getY();
            // The same check as we did before; just check the vector against the size of the leaf
            if((Math.abs(leafVectorX) < ((size.getX() + leafAtQuadrant.getSize().getX())/2)) && (Math.abs(leafVectorY) < ((size.getY()+leafAtQuadrant.getSize().getY())/2))){processQueue.add(currentNode); break;}
            // If neither of those guards pass then enqueue the leafAtQuadrant
            processStack.push(leafAtQuadrant);
        }*/
        ArrayList<QuadtreeNode> returnValue = new ArrayList<QuadtreeNode>();
        // Same as getting the insertion algorithm
        QuadtreeNode selectedNode = getFirstNode(position, size);
        LinkedList<QuadtreeNode> processQueue = new LinkedList<QuadtreeNode>();
        // Enqueue the first node to check
        processQueue.add(selectedNode);
        while(!processQueue.isEmpty()){
            QuadtreeNode currentNode = processQueue.removeFirst();
            // If the node is not a leaf, add its children assuming the given bounds intersects
            if(!currentNode.isLeaf()){
                QuadtreeNode NELeaf = currentNode.getNodeFromEnum(Leaf.NE); 
                if(intersectsWith(size, position, NELeaf)){processQueue.add(NELeaf);}
                
                QuadtreeNode NWLeaf = currentNode.getNodeFromEnum(Leaf.NW); 
                if(intersectsWith(size, position, NWLeaf)){processQueue.add(NWLeaf);}

                QuadtreeNode SELeaf = currentNode.getNodeFromEnum(Leaf.SE); 
                if(intersectsWith(size, position, SELeaf)){processQueue.add(SELeaf);}

                QuadtreeNode SWLeaf = currentNode.getNodeFromEnum(Leaf.SW); 
                if(intersectsWith(size, position, SWLeaf)){processQueue.add(SWLeaf);}
            } else {
                // Otherwise insert it into the return value
                returnValue.add(currentNode);
            }
        }
        return returnValue;
    }
    // Instance vars
    QuadtreeNode root; // The root has no geohash so only the first division will have the geohash
    private int maximumDepth, maxChilds;  // How high the depth can be, takes priority over maxChilds but below MinimumLeafSize. MaxChilds indicates how many objects can be in a node before we divide it
    private Vector2 minimumLeafSize = new Vector2(20,20);
    private int nodesCreated; // Alright this purely serves as a way to assign each node a unique ID, since geohashes can change, its not a good way of uniquely identifying each quadtree
    private byte depth; // SInce our geohash can only support aboouut 32 layers, we don't need too many bits allocated to the depth here
    // Accessors
    public Vector2 getSize(){return this.root.getSize();}
    public Vector2 getPosition(){return this.root.getPosition();}
    // Instance methods
    /**
     * Adds the given object to the quadtree at the position and the size
     * @param item the object to add
     * @param position the position to add the object to
     * @param size the size of the object for the purposes of spatial querying
     */
    public void add(Obj item, Vector2 position, Vector2 size){
        // Like in our lua code, we want to do a couple of steps
        //  Depth first search to find the first node that can't fit the item with the size and position specified
        QuadtreeNode firstNode = getFirstNode(size, position);
        LinkedList<QuadtreeNode> breadthFirstQueue = new LinkedList<QuadtreeNode>(); // yknow what I can probably do it in one pass but im porting my algorithm over
        breadthFirstQueue.add(firstNode);
        // Create a container for the item
        QuadtreeObjectContainer<Obj> container = new QuadtreeObjectContainer<Obj>(size, position, item);
        // Start up the breadth first search
        while(!breadthFirstQueue.isEmpty()){
            QuadtreeNode currentNode = breadthFirstQueue.peek(); // We don't dequeue at first since there's a condition where we need to re-check the same node
            //System.out.printf("%1$s size, %2$s position\n", currentNode.getSize(), currentNode.getPosition());
            // if the currentNode is a leaf then check to see if it can be divided
            if(currentNode.isLeaf()){
                // If dividing the currentNode means its leaves become smaller than the minimumSize, we don't carry on
                if((currentNode.getSize().getX()/2 > this.minimumLeafSize.getX()) && (currentNode.getSize().getY()/2 > this.minimumLeafSize.getY())){
                    // If the node's objects size is equal to the maxChilds config then split the node
                    if(currentNode.getObjects().size() >= maxChilds){
                        //System.out.println("Splitting node");
                        // Divide the node
                        currentNode.divideNode();
                        // If the depth of the current node  + 1 is greater than the depth set in this instance, set the depth to current node.depth + 1
                        if(currentNode.getDepth() + 1 > this.depth){this.depth = (byte) (currentNode.getDepth() + 1);}
                        // And shuffle the children down
                        moveObjectsToLeaves(currentNode);
                        continue; // Re-do the check with the new split node
                    }
                }
                // Otherwise, if the leaf can't divide we just insert it to that
                currentNode.addObject(container);
            } else {
                //Otherwise, go through each leaf and any nodes that the item with the size at that position overlaps with, enqueue that node to check
                QuadtreeNode NELeaf = currentNode.getNodeFromEnum(Leaf.NE); 
                if(intersectsWith(size, position, NELeaf)){breadthFirstQueue.add(NELeaf);
                    //System.out.println("NE");
                }
                QuadtreeNode NWLeaf = currentNode.getNodeFromEnum(Leaf.NW); 
                if(intersectsWith(size, position, NWLeaf)){breadthFirstQueue.add(NWLeaf);
                    //System.out.println("NW");
                }
                QuadtreeNode SELeaf = currentNode.getNodeFromEnum(Leaf.SE); 
                if(intersectsWith(size, position, SELeaf)){breadthFirstQueue.add(SELeaf);
                    //System.out.println("SE");
                }
                QuadtreeNode SWLeaf = currentNode.getNodeFromEnum(Leaf.SW); 
                if(intersectsWith(size, position, SWLeaf)){breadthFirstQueue.add(SWLeaf);
                    //System.out.println("SW");
                }
            }
            // Dequeue the node because we're done with it
            breadthFirstQueue.removeFirst();
        }
        return;
    }
    /**
     * Removes the given object from the quadtree given a quadtree container
     * <p> I'll admit this one doesn't really do anything because under no circumstances should an external instnace have QuadtreeObjectContainer references
     * @param object
     */
    public void remove(QuadtreeObjectContainer<Obj> object){
        // Since every instance is a reference, we can probably just get away with going through every node in the object's container
        ArrayList<QuadtreeNode> objectNodes = object.getQuadtreeNodes();
        for(int i = objectNodes.size() - 1; i >= 0; i--){
            // Start from the other way around
            QuadtreeNode currentNode = objectNodes.get(i);
            // Remove the reference in each node stored in the container's nodes array
            ArrayList<QuadtreeObjectContainer<Obj>> objectsInNode = currentNode.getObjects();
            for(int l = 0; l < objectsInNode.size(); l++){
                if(objectsInNode.get(l) == object){
                    objectsInNode.remove(l);
                    break;
                }
            }
            // Then remove it from the objectNodes array
            objectNodes.remove(i);
        }
    }
    /**
     * Removes the given object using the position placed at to fnd the object in question
     * @param object
     * @param position
     */
    public void remove(Obj object, Vector2 position){
        // Navigates to the first node that fits that position to grab the object that matches the memory address of the given object
        QuadtreeNode firstNode = this.getNodeAtPosition(position);
        ArrayList<QuadtreeObjectContainer<Obj>> objects = firstNode.getObjects();
        for(int i = 0; i < objects.size(); i++){
            QuadtreeObjectContainer<Obj> currentContainer = objects.get(i);
            if(currentContainer.getInstance() == object){
                this.remove(currentContainer);
                break;
            }
        }
    }
    /**
     * Decodes the given geohash to locate the node that the geohash corresponds to
     */
    public QuadtreeNode getNodeFromGeohash(long geohash){
        // Iteratively perform a depth-first search to locate the node with the given geohash, using the 2Nth bits to work out which quadrant to check
        QuadtreeNode returnValue = null;
        LinkedList<QuadtreeNode> processStack = new LinkedList<QuadtreeNode>();
        byte currentDepth = 0;
         // Starting from the root, extract the first two bits from the geohash
         processStack.push(this.root);
         while (!processStack.isEmpty()){
            QuadtreeNode currentNode = processStack.pop();
            // Since 00 is a perfectly valid geohash header, we sort of have to go until we reach a leaf node and then try to compare the geohash
            if(currentNode.isLeaf()){
                // If its a leaf then we can't go any deeper so in all likelihood we've found the node with the given geohash
                // But just to be safe, we can check the geohash of the node against the geohash we've provided and see if its equal
                if(currentNode.getGeohash() != geohash){throw new IllegalArgumentException("Given geohash of "+ String.valueOf(geohash) + "does not match node");}
                returnValue = currentNode;
                break;
            }
            // Create a bit mask
            long bitMask = 3; // Starting at 00...011, bitshift the mask by the (depth*2 [or bitshifting it once]) to get the bits of the next layer of the geohash
            bitMask <<=(depth << 1);
            long maskedBits = geohash & bitMask; // Bitwse AND it with the geohash to get the set of two 
            // Then bitshift it back so that our range is back in [0, 3]
            maskedBits >>=(depth << 1);
            if(maskedBits == 0){ // SE
                processStack.push(currentNode.getNodeFromEnum(Leaf.SE));
            }else if(maskedBits == 1){ // SW
                processStack.push(currentNode.getNodeFromEnum(Leaf.SW));
            }else if(maskedBits == 2){ // NE
                processStack.push(currentNode.getNodeFromEnum(Leaf.NE));
            }else if(maskedBits == 3){ // NW
                processStack.push(currentNode.getNodeFromEnum(Leaf.NW));
            }
         }

        return returnValue;
    }
    /**
     * Fetches all objects that are within the node closest to the point
     * <p> this doesn't filter any farther by checking for overlaps between the objects and point notably
     * @param point
     * @return
     */
    public ArrayList<Obj> getObjectsAtPoint(Vector2 point){
        // So the issue is that we can't have an array of generic types without doing something stupid
        ArrayList<Obj> returnValue = new ArrayList<Obj>(10);
        // Grab the node closest to the point
        QuadtreeNode closestPoint = getNodeAtPosition(point);
        // Then we just grab the node at the given position
        ArrayList<QuadtreeObjectContainer<Obj>> objects = closestPoint.getObjects();
        for(int i = 0; i < objects.size(); i++){
            // Then extract the instance from the object and add it to returnValue
            returnValue.add(objects.get(i).getInstance());
        }
        return returnValue;
    }
    /**
     * Fetches all the objects that fall within the nodes that overlap with the size at the position
     * <p> Notably, this also doesn't do any further filtering to exclude objectst that aren't explicitly overlapping with the rectange defined by the size and position
     * @param position
     * @param size
     * @return
     */
    public ArrayList<Obj> getObjectsInArea(Vector2 position, Vector2 size){
        ArrayList<Obj> returnValue = new ArrayList<Obj>(10);
        ArrayList<QuadtreeNode> nodesInArea = getNodesInArea(position, size);
        // Unpack each item in the nodesInArea array
        for(int i = 0; i<nodesInArea.size();i++){
            QuadtreeNode currentNode = nodesInArea.get(i);
            ArrayList<QuadtreeObjectContainer<Obj>> objectList = currentNode.getObjects();
            // Then loop through the objectList, unpacking the instance from the container
            for(int l = 0; l < objectList.size(); l++){
                QuadtreeObjectContainer<Obj> currentObject = objectList.get(l);
                returnValue.add(currentObject.getInstance());
            }
        }
        return returnValue;
    }
    // And yknow the overloads to those

    // Constructors
    public Quadtree(Vector2 treeSize, Vector2 treePosition){
        super();
        // Create a quadtree node at the root and split it so that we have a geohash assigned
        this.root = new QuadtreeNode(treeSize, treePosition, 0);
        this.root.divideNode(); this.depth = 1;  this.maxChilds = defaultMaxChilds;
    }
    // Inner class
    private class QuadtreeObjectContainer<T>{ // To be honest I'm not too happy using another generic here instead of using the object that's already a generic in the quadtree declaration
        // The objectContainer just stores the position and size info so that we can easily access those when fetching items in addition to making it easier to remove the nodes this object is in
        private Vector2 position, size; // The size of the object at the given position
        private T instance; // The object stored at that location
        private ArrayList<QuadtreeNode> quadtreeNodes; // Contains a list of quadtreeNodes so that we know which objects overlap with which node
        // Methods
        public ArrayList<QuadtreeNode> getQuadtreeNodes(){return this.quadtreeNodes;}
        public Vector2 getPosition(){return this.position;} public Vector2 getSize(){return this.size;}
        public T getInstance(){return this.instance;}
        public QuadtreeObjectContainer(Vector2 size, Vector2 position, T instance){
            super(); 
            this.position = position; 
            this.size = size; 
            this.instance = instance; 
            this.quadtreeNodes = new ArrayList<QuadtreeNode>();}
    }

    private class QuadtreeNode{
        private QuadtreeNode NE, NW, SE, SW;
        private long geohash; // Now I think we'll never reach 32 layers of quadtrees so we'll use a long to assign geohashes 
        private Vector2 size, position; // The size of the node
        private int depth; // The root's depth is 0 so our first 4 child nodes are a depth of 1
        private ArrayList<QuadtreeObjectContainer<Obj>> objects;
        // Mutators
        //  NO MUTATORS
        // Accessor
        public int getDepth(){return this.depth;}
        public long getGeohash(){return this.geohash;}
        public Vector2 getSize(){return this.size;}
        public Vector2 getPosition(){return this.position;}
        public QuadtreeNode getNodeFromEnum(Leaf inputEnum){
            QuadtreeNode returnValue = this.SW; 
            switch(inputEnum){
                case NE:returnValue=this.NE; break;
                case NW:returnValue=this.NW; break;
                case SE:returnValue=this.SE;break;
                case SW:break;
            }
            return returnValue;}
        public ArrayList<QuadtreeObjectContainer<Obj>> getObjects(){return this.objects;}
        // Instance methods
        public boolean isLeaf(){return ((this.NE == null) && (this.NW == null) && (this.SE == null )&& (this.SW == null));}
        public QuadtreeObjectContainer<Obj> addObject(Obj object, Vector2 position, Vector2 size){ // While adding takes place in the node level, removing is done at the Quadtree level since we have to remove objects from multiple nodes if need be
            // Since we do our object checks in the insertion code, we just insert this into the linked list with no hassle
            // Wrap the object with a QuadtreeObjectContainer
            QuadtreeObjectContainer<Obj> wrapper = new QuadtreeObjectContainer<Obj>(size, position, object);
            // Insert that into the objects list
            this.objects.add(wrapper);
            // Insert a reference to this node to the Object's collection of nodes
            wrapper.getQuadtreeNodes().add(this);
            // Return the container so that we can remove the object from each node its been inserted to
            return wrapper;
        }
        public QuadtreeObjectContainer<Obj> addObject(QuadtreeObjectContainer<Obj> container){
            // This overload lets us just re-insert an object container to the node's list
            this.objects.add(container);
            container.getQuadtreeNodes().add(this);
            //System.out.printf("Added item %1$s to node with geohash %2$s with position %3$s and size %4$s\n", container.getInstance().toString(), String.valueOf(this.geohash), this.position, this.size);
            return container;
        }
        public void divideNode(){
            // Divides the node by adding leaf nodes to this quadtree's NE, SW, SE and SW
            if(!this.isLeaf()){return;} // Don't do anything for nodes that aren't already leaves
            Vector2 divisionSize = Vector2.divide(this.size, 2);
            // Construct new nodes for each quadrant
            this.NE = new QuadtreeNode(divisionSize, new Vector2(this.position.getX() + divisionSize.getX()/2, this.position.getY() + divisionSize.getY()/2), this.depth + 1, this.geohash, Leaf.NE); // NE is X+, Y+
            this.NW = new QuadtreeNode(divisionSize, new Vector2(this.position.getX() - divisionSize.getX()/2, this.position.getY() + divisionSize.getY()/2), this.depth + 1,  this.geohash, Leaf.NW); // NW is X-, Y+
            this.SE = new QuadtreeNode(divisionSize, new Vector2(this.position.getX() + divisionSize.getX()/2, this.position.getY() - divisionSize.getY()/2), this.depth + 1,  this.geohash, Leaf.SE); // SE is X+, Y-            
            this.SW = new QuadtreeNode(divisionSize, new Vector2(this.position.getX() - divisionSize.getX()/2, this.position.getY() - divisionSize.getY()/2), this.depth + 1,  this.geohash, Leaf.SW); // SW is X-, Y-
        }
        // Constructors
        public QuadtreeNode(Vector2 size, Vector2 position, int depth){
            this.objects = new ArrayList<QuadtreeObjectContainer<Obj>>(10); // 10 is small enough so that we woon't have too much issue
            this.size = size; this.position = position; this.depth = depth;
        }
        // And an override to set the geohash
        public QuadtreeNode(Vector2 size, Vector2 position, int depth, long parentGeohash, Leaf quadrant){
            this(size, position, depth);
            this.geohash = Quadtree.computeGeohash(parentGeohash, quadrant, depth);
        }
    }
}
