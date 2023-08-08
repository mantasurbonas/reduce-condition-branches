package lt.twoday.extractmethodmarker;

import java.util.UUID;

import org.openrewrite.marker.Marker;

public class BlockMark implements Marker {
    
    public BlockMark parent = null;
    
    public int statementCount = 0;
    public int nestingDepth   = 0;
        
    public boolean fitsForExtractMethod = false;
    public boolean hasMarkedChildren = false;
    
    private UUID uuid;
    
    public BlockMark(UUID id) {
        this.uuid = id;
    }
    
    public BlockMark clone(UUID uuid) {
        BlockMark ret = new BlockMark(uuid);
            ret.statementCount = 0;
            ret.nestingDepth = this.nestingDepth;

            ret.parent = this;
            
            ret.hasMarkedChildren = false;
        return ret;
    }

    public void setFitsForExtractMethod() {
        this.fitsForExtractMethod = true;
        
        BlockMark parent = this.parent;
        while (parent != null) {
            parent.fitsForExtractMethod = false;
            parent.hasMarkedChildren = true;
            parent = parent.parent;
        }
    }
    
    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public BlockMark withId(UUID id) {
        BlockMark ret = new BlockMark(id);
            ret.statementCount = this.statementCount;
            ret.nestingDepth = this.nestingDepth;
            ret.parent = this.parent;
            ret.hasMarkedChildren = this.hasMarkedChildren;
        return ret;
    }

    @Override
    public String toString() {
        return "loc: " + statementCount
                + " depth: " + nestingDepth 
                + " fits: " + fitsForExtractMethod 
                + " hasMarkedChildren: " + hasMarkedChildren;
    }

}
