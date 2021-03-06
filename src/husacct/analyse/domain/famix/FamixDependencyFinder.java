package husacct.analyse.domain.famix;

import husacct.common.dto.DependencyDTO;

import java.util.ArrayList;
import java.util.List;

class FamixDependencyFinder extends FamixFinder {

    private static enum FinderFunction {
        FROM, TO, BOTH, ALL
    };
    private FinderFunction currentFunction;
    private List<DependencyDTO> dependencyCache;
    private boolean filtered;
    private String[] filter;
    private String from = "", to = "";
    private List<DependencyDTO> currentResult;

    public FamixDependencyFinder(FamixModel model) {
        super(model);
        this.currentResult = new ArrayList<DependencyDTO>();
        this.filter = new String[]{};
        this.filtered = false;
        this.currentFunction = FinderFunction.BOTH;
        this.dependencyCache = null;
    }

    public List<DependencyDTO> getAllDependencies() {
        this.currentResult.clear();
        performQuery(FinderFunction.ALL, "", "");
        if (dependencyCache == null) {
            dependencyCache = new ArrayList<DependencyDTO>();
            this.dependencyCache.addAll(this.currentResult);
        }
        return this.dependencyCache;
    }

    public List<DependencyDTO> getDependencies(String from, String to) {
        this.currentResult.clear();
        performQuery(FinderFunction.BOTH, from, to);
        return this.currentResult;
    }

    public List<DependencyDTO> getDependencies(String from, String to, String[] dependencyFilter) {
        this.currentResult.clear();
        performQuery(FinderFunction.BOTH, from, to, dependencyFilter);
        return this.currentResult;
    }

    public List<DependencyDTO> getDependenciesFrom(String from, String[] dependencyFilter) {
        this.currentResult.clear();
        performQuery(FinderFunction.FROM, from, "", dependencyFilter);
        return this.currentResult;
    }

    public List<DependencyDTO> getDependenciesFrom(String from) {
        this.currentResult.clear();
        performQuery(FinderFunction.FROM, from, "");
        return this.currentResult;
    }

    public List<DependencyDTO> getDependenciesTo(String to) {
        this.currentResult.clear();
        performQuery(FinderFunction.TO, "", to);
        return this.currentResult;
    }

    public List<DependencyDTO> getDependenciesTo(String to, String[] dependencyFilter) {
        this.currentResult.clear();
        performQuery(FinderFunction.TO, "", to, dependencyFilter);
        return this.currentResult;
    }

    private void performQuery(FinderFunction function, String argumentFrom, String argumentTo, String[] applyFilter) {
        this.filter = applyFilter;
        this.filtered = true;
        performQuery(function, argumentFrom, argumentTo);
    }

    private void performQuery(FinderFunction function, String argumentFrom, String argumentTo) {
        this.currentFunction = function;
        this.from = argumentFrom;
        this.to = argumentTo;
        this.currentResult = findDependencies();
        this.reset();
    }

    private void reset() {
        this.removeFilter();
    }

    private void removeFilter() {
        this.filtered = false;
        this.filter = new String[]{};
    }

    private List<DependencyDTO> findDependencies() {
        List<DependencyDTO> result = new ArrayList<DependencyDTO>();
        List<FamixAssociation> allAssocations = theModel.associations;
        for (FamixAssociation assocation : allAssocations) {
            if (compliesWithFunction(assocation) && compliesWithFilter(assocation)) {
                DependencyDTO foundDependency = buildDependencyDTO(assocation, false);
                if (!containsDependency(foundDependency, result)) {
                    result.add(foundDependency);
                }
            }
        }
        if (!this.from.equals("")) {
        	result.addAll(this.findIndirectDependencies(result));
        }
        return result;
    }

    private List<DependencyDTO> findIndirectDependencies(List<DependencyDTO> Dependencies) {
        List<DependencyDTO> tempDirectDependencies = Dependencies;
        List<DependencyDTO> returnIndirectDependencies = new ArrayList<DependencyDTO>();
        for (DependencyDTO directDependency : tempDirectDependencies) {
            List<DependencyDTO> indirectDependenciesForDirectDependency = findDependenciesRelatedTo(tempDirectDependencies, directDependency);
            for (DependencyDTO indirectDependency : indirectDependenciesForDirectDependency) {
                DependencyDTO tempDTO = new DependencyDTO(directDependency.from, indirectDependency.to, directDependency.type + "" + indirectDependency.type, true, directDependency.lineNumber);
                if(isValidIndirectDependency(tempDTO)){
	                if (!returnIndirectDependencies.contains(tempDTO) && !containsDependency(tempDTO, returnIndirectDependencies) && !isDependencyInList(tempDTO, returnIndirectDependencies)) {
	                    returnIndirectDependencies.add(tempDTO);
	                }
                }
            }
        }
        return returnIndirectDependencies;
    }
    
    private boolean isValidIndirectDependency(DependencyDTO dependency){
    	if(dependency.type.matches("Extends.*?Extends.*"))
    		return true;
    	if(dependency.type.matches("Extends.*?Implements.*"))
    		return true;
    	if(dependency.type.matches("Implements.*?Extends.*"))
    		return true;
    	return false;
    }

    private boolean isDependencyInList(DependencyDTO dependency, List<DependencyDTO> allDependencies) {
        for (DependencyDTO loopingDependency : allDependencies) {
            if (loopingDependency.from.equals(dependency.from) && loopingDependency.to.equals(dependency.to) && loopingDependency.type.equals(dependency.type) && loopingDependency.lineNumber == dependency.lineNumber) {
                return true;
            }
        }
        return false;
    }

    private List<DependencyDTO> findDependenciesRelatedTo(List<DependencyDTO> allDependencies, DependencyDTO relatedTo) {
        List<DependencyDTO> returnArray = new ArrayList<DependencyDTO>();
        for (DependencyDTO dependency : allDependencies) {
            if (dependency.from.equals(relatedTo.to)) {
                returnArray.add(dependency);
            }
        }
        return returnArray;
    }

    private boolean containsDependency(DependencyDTO find, List<DependencyDTO> dependencies) {
        for (DependencyDTO d : dependencies) {
            if (find.equals(d)) {
                return true;
            }
        }
        return false;
    }

    private List<DependencyDTO> findIndirectDependenciesFrom(DependencyDTO fromDependency) {
        List<DependencyDTO> found = new ArrayList<DependencyDTO>();
        this.from = fromDependency.to;
        this.to = fromDependency.to;
        for (DependencyDTO indirectDependency : findDependencies()) {
            indirectDependency.isIndirect = true;
            if (!found.contains(indirectDependency) && !isPackage(indirectDependency.from)) {
                found.add(indirectDependency);
            }
            if (!indirectDependency.to.equals(fromDependency.from)) {
                for (DependencyDTO subIndirect : this.findIndirectDependenciesFrom(indirectDependency)) {
                    if (!containsDependency(subIndirect, found)) {
                        found.add(subIndirect);
                    }
                }
            }
        }
        return found;
    }

    private boolean isPackage(String uniquename) {
        return theModel.packages.containsKey(uniquename);
    }

    private boolean compliesWithFunction(FamixAssociation association) {
        switch (this.currentFunction) {
            case BOTH:
                return connectsBoth(association, from, to);
            case FROM:
                return isFrom(association, from);
            case TO:
                return isTo(association, to);
            case ALL:
                return true;
        }
        return false;
    }

    private boolean compliesWithFilter(FamixAssociation association) {
        if (!filtered) {
            return true;
        }
        for (String value : filter) {
            if (association.type.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean connectsBoth(FamixAssociation association, String from, String to) {
        return isFrom(association, from) && isTo(association, to);
    }

    private boolean isFrom(FamixAssociation association, String from) {
        boolean result = from.equals("") || association.from.equals(from);
        result = result || association.from.startsWith(from + ".");
        result = result && !association.from.equals(association.to);
        return result;
    }

    private boolean isTo(FamixAssociation association, String to) {
        boolean result = to.equals("") || association.to.equals(to);
        result = result || association.to.startsWith(to + ".");
        result = result && !association.to.equals(association.from);
        return result;
    }

    private DependencyDTO buildDependencyDTO(FamixAssociation association, boolean isIndirect) {
        String dependencyFrom = association.from;
        String dependencyTo = association.to;
        String dependencyType = association.type;
        int dependencyLine = association.lineNumber;
        return new DependencyDTO(dependencyFrom, dependencyTo, dependencyType, isIndirect, dependencyLine);
    }
}
