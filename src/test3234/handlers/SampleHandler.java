package test3234.handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

public class SampleHandler extends AbstractHandler {
	public static final String INSIDE_JDTUI = "inside_jdtui";
	public static final String[] FORBIDDEN = new String[] {
			//INSIDE_JDTUI, 
			"org.eclipse.search_3.15.100.v20230727-0604.jar",
			"org.eclipse.ltk.ui.refactoring_3.13.100.v20230728-0612.jar",
			"org.eclipse.ui.forms_3.13.0.v20230727-0604.jar",
			"org.eclipse.ui.navigator_3.12.100.v20230727-0604.jar",
			"org.eclipse.ui.navigator.resources_3.9.100.v20230727-0604.jar",
			"org.eclipse.debug.ui_3.18.100.v20230731-1549.jar",
			"org.eclipse.compare_3.9.200.v20230726-0617.jar",
			"org.eclipse.team.ui_3.10.100.v20230726-0617.jar",
			"org.eclipse.jface.text_3.24.100.v20230727-0604.jar",
			"org.eclipse.ui_3.203.200.v20230727-0604.jar",
			"org.eclipse.swt_3.124.100.v20230724-1304.jar",
			"org.eclipse.swt.gtk.linux.x86_64_3.124.100.v20230724-1304.jar",
			"org.eclipse.jface_3.30.100.v20230727-0604.jar",
			"org.eclipse.ui.workbench_3.129.100.v20230727-0604.jar",
			"org.eclipse.e4.ui.workbench3_0.17.100.v20230727-0604.jar",
			"org.eclipse.ui.console_3.13.0.v20230726-0617.jar",
			"org.eclipse.ui.workbench.texteditor_3.17.100.v20230727-0604.jar",
			"org.eclipse.ui.ide_3.21.100.v20230727-0604.jar",
			"org.eclipse.e4.ui.ide_3.17.100.v20230727-0604.jar",
			"org.eclipse.ui.views_3.12.100.v20230727-0604.jar",
			"org.eclipse.ui.editors_3.16.100.v20230727-0604.jar",
	};
			
	private static class MyJob extends Job {
		private HashMap<String, String> typeFqcnToJar = null;
		private HashMap<IType, Boolean> typeToUiCache = new HashMap<IType, Boolean>();;

		public MyJob(String name) {
			super(name);
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			IWorkbenchWindow ww = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			IWorkspace w = ResourcesPlugin.getWorkspace();
			IProject[] all = w.getRoot().getProjects();
			IProject jdtUi = w.getRoot().getProject("org.eclipse.jdt.ui");
			IProject manipulations = w.getRoot().getProject("org.eclipse.jdt.core.manipulations");
			System.out.println("jdtui: " + jdtUi.exists() + ", manip: " + manipulations.exists());

			boolean runAllTypes = true;
			IJavaProject jp = JavaCore.create(jdtUi);
			if( typeFqcnToJar == null ) {
				typeFqcnToJar = new HashMap<>();
				//cacheAllTypes(jp, true);
				cacheAllTypes(jp, runAllTypes);
			}
			if( runAllTypes ) 
				handleProject(jp);
			else {
				String typeName = "org.eclipse.jdt.ui.text.IJavaPartitions";
				IType t;
				try {
					t = jp.findType(typeName);
					if( t != null ) {
						handleOneUnit(jp, t.getCompilationUnit());
					}
				} catch (JavaModelException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			return Status.OK_STATUS;
		}

		private void cacheAllTypes(IJavaProject jp, boolean checkDeps) {
			try {
				IPackageFragment[] el = jp.getPackageFragments();
				for (int i = 0; i < el.length; i++) {
					IPackageFragment fragment = el[i];
					if (el[i].getKind() == IPackageFragmentRoot.K_SOURCE) {
						ICompilationUnit[] units = fragment.getCompilationUnits();
						for( int k = 0; k < units.length; k++ ) {
							IType[] allTypes = units[k].getAllTypes();
							for( int m = 0; m < allTypes.length; m++ ) {
								IType mt = allTypes[m];
								String name = mt.getFullyQualifiedName();
								typeFqcnToJar.put(name, INSIDE_JDTUI);
								System.out.println("Caching " + name + ": " + typeToUiCache.get(mt));
								if( checkDeps ) {
									typeHasUiDepsWrapper(jp, mt, new ArrayList<IType>());
								}
							}
						}
					} else {
						IClassFile[] classez = el[i].getAllClassFiles();
						for( int k = 0; k < classez.length; k++ ) {
							IClassFile cf = classez[k];
							try {
								String name = cf.getType().getFullyQualifiedName();
								String parentName = cf.getParent().getParent().getElementName();
								typeFqcnToJar.put(name, parentName);
								System.out.println("Caching " + name + " in " + parentName + ", " + parentName);
							} catch(UnsupportedOperationException u) {
								// ignore
							}
						}
					}
				}
				System.out.println(el);
			} catch (JavaModelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		private void handleProject(IJavaProject jp) {
			try {
				IPackageFragment[] el = jp.getPackageFragments();
				for (int i = 0; i < el.length; i++) {
					if (el[i].getKind() == IPackageFragmentRoot.K_SOURCE) {
						handlePackageFragment(jp, el[i]);
					}
				}
				System.out.println(el);
			} catch (JavaModelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		private boolean typeHasUiDepsWrapper(IJavaProject jp, IType type, ArrayList<IType> inProgress) throws JavaModelException {
			if( type == null ) {
				System.out.println("Break");
			}
			if( type.getElementName().equals("ContentAssistHistory")) {
				System.out.println("Break");
			}
			if( typeToUiCache.get(type) != null ) {
				return typeToUiCache.get(type);
			}
			boolean ret = typeHasUiDeps2(jp, type, inProgress);
			typeToUiCache.put(type, ret);
			return ret;
		}
		private boolean typeHasUiDeps2(IJavaProject jp, IType type, ArrayList<IType> inProgress) throws JavaModelException {
			ICompilationUnit unit = type.getCompilationUnit();
			if( unit == null ) {
				String fqqn = type.getFullyQualifiedName();
				String v = typeFqcnToJar.get(fqqn);
				if(Arrays.asList(FORBIDDEN).contains(v)) {
					System.out.println(type.getElementName() + " is in a forbidden jar");
					return true;
				}
				return false;
			}
			
			if( inProgress.contains(type)) {
				// circular dep, abort
				return true;
			}
			
			ArrayList<IType> inProgress2 = new ArrayList<IType>(inProgress);
			inProgress2.add(type);
			IImportDeclaration[] imports = unit.getImports();
			if (!hasZeroUiImports(imports)) {
				System.out.println(unit.getElementName() + " has UI imports");
				return true;
			}
			
			for( int i = 0; i < imports.length; i++ ) {
				if( !imports[i].getElementName().contains("*")) {
					IType t = unit.getJavaProject().findType(imports[i].getElementName());
					if( t != null && typeHasUiDepsWrapper(jp, t, inProgress2)) {
						System.out.println(unit.getElementName() + " has an import with UI deps: " + t.getElementName());
						return true;
					}
				}
			}
			
			ITypeHierarchy h = type.newTypeHierarchy(new NullProgressMonitor());
			IType[] superTypes = h.getSupertypes(type);
			for( int i = 0; i < superTypes.length; i++ ) {
				if( typeHasUiDepsWrapper(jp, superTypes[i], inProgress2)) {
					System.out.println(unit.getElementName() + " has a UI superclass: " + superTypes[i].getElementName());
					return true;
				}
			}

			IType[] interfaces = h.getAllSuperInterfaces(type);
			for( int i = 0; i < interfaces.length; i++ ) {
				if( typeHasUiDepsWrapper(jp, interfaces[i], inProgress2)) {
					System.out.println(unit.getElementName() + " has a UI interface: " + superTypes[i].getElementName());
					return true;
				}
			}

			IField[] allFields = type.getFields();
			for( int i = 0; i < allFields.length; i++ ) {
				String signature = allFields[i].getTypeSignature();
				IType fieldType = signatureToType(signature, unit, imports);
				if( fieldType != null && !type.getCompilationUnit().equals(fieldType.getCompilationUnit())) {
					if( typeHasUiDepsWrapper(jp, fieldType, inProgress2)) {
						System.out.println(unit.getElementName() + " has a UI field: " + signature);
						return true;
					}
				}
			}
			
			IMethod[] methods = type.getMethods();
			for( int i = 0; i < methods.length; i++ ) {
				ILocalVariable[] params = methods[i].getParameters();
				for( int j = 0; j < params.length; j++ ) {
					String signature = params[j].getTypeSignature();
					IType paramType = signatureToType(signature, unit, imports);
					if( paramType != null && !type.getCompilationUnit().equals(paramType.getCompilationUnit())) {
						if( typeHasUiDepsWrapper(jp, paramType, inProgress2)) {
							System.out.println(unit.getElementName() + " has a UI method param: " + signature);
							return true;
						}
					}
				}
				
				// Now handle return
				String retSignature = methods[i].getReturnType();
				IType retType = signatureToType(retSignature, unit, imports);
				if( retType != null && !type.getCompilationUnit().equals(retType.getCompilationUnit())) {
					if( typeHasUiDepsWrapper(jp, retType, inProgress2)) {
						System.out.println(unit.getElementName() + " has a UI return value: " + retSignature);
						return true;
					}
				}
			}
			
			return false;
		}
		
		private IType signatureToType(String signature, ICompilationUnit unit, IImportDeclaration[] imports) throws JavaModelException {
			String qualifier = Signature.getSignatureQualifier(signature);
			String simpleName = Signature.getSignatureSimpleName(signature);
			if( simpleName.endsWith("[]")) {
				simpleName = simpleName.substring(0, simpleName.length()-2);
			}
			if( (qualifier == null || qualifier.isEmpty()) ) {
				boolean importFound = false;
				for( int z = 0; z < imports.length && !importFound; z++ ) {
					if( imports[z].getElementName().endsWith("." + simpleName)) {
						qualifier = imports[z].getElementName();
						importFound = true;
					}
				}
			}
			if( qualifier == null || qualifier.isEmpty()) {
				IPackageDeclaration[] decs = unit.getPackageDeclarations();
				if( decs != null && decs.length > 0 ) 
					qualifier = unit.getPackageDeclarations()[0].getElementName();
			}
			String fq =  qualifier.isEmpty() ? simpleName : qualifier + "." + simpleName;
			IType t = unit.getJavaProject().findType(fq);
			return t;
		}
		
		private boolean canCopy(IJavaProject jp, ICompilationUnit unit) throws JavaModelException {
			IImportDeclaration[] imports = unit.getImports();
			if (!hasZeroUiImports(imports)) {
				System.out.println(unit.getElementName() + " has UI imports");
				return false;
			}
			
			IType[] all = unit.getAllTypes();
//			if( all.length != 1 || !all[0].isInterface()) {
//				System.out.println(unit.getElementName() + " IGNORED FOR NOW");
//				return false;
//			}
			
			
			for( int z = 0; z < all.length; z++ ) {
				if( typeHasUiDepsWrapper(jp, all[z], new ArrayList<IType>())) {
					System.out.println(unit.getElementName() + " has UI deps");
					return false;
				}
			}
			boolean isMessages = unit.getResource().getName().endsWith("Messages.java");
			if( isMessages ) {
				System.out.println(unit.getElementName() + " IGNORED Messages");
				return false;
			}
			
			if( unit.getResource().getProjectRelativePath().toString().contains("jar in jar")) {
				System.out.println(unit.getElementName() + " IGNORED jar in jar");
				return false;
			}
			return true;
		}

		private void handlePackageFragment(IJavaProject jp, IPackageFragment pkg) throws JavaModelException {
			ICompilationUnit[] units = pkg.getCompilationUnits();
			for (ICompilationUnit unit : units) {
				handleOneUnit(jp, unit);
			}
		}
		private void handleOneUnit(IJavaProject jp, ICompilationUnit unit) throws JavaModelException {
			String unitName = unit == null ? "null" : unit.getElementName();
			if( unitName.toLowerCase().contains("IJavaPartitions".toLowerCase())) {
				System.out.println("Break");
			}
			if(canCopy(jp, unit)) {				
				IPath project = unit.getResource().getProject().getLocation();
				IPath projectRelative = unit.getResource().getProjectRelativePath();
				IPath resource = project.append(projectRelative);
				
				IPath destProject = new Path("/home/rob/code/work/ide/eclipse/jdt/eclipse.jdt.ui/org.eclipse.jdt.core.manipulation");
				String srcFolder = projectRelative.segment(0);
				IPath srcFolderRelative = projectRelative.removeFirstSegments(1);

				srcFolder = srcFolder.replace("core refactoring", "refactoring")
						.replace("ui refactoring", "refactoring")
						.replace("ui", "core ui");
				IPath destResourceAbsolute = destProject.append(srcFolder).append(srcFolderRelative);
				System.out.println("CAN MOVE " + projectRelative.toOSString() + " to " + destResourceAbsolute);
				
				File parentFolder = destResourceAbsolute.removeLastSegments(1).toFile();
				parentFolder.mkdirs();
				
				try {
					Files.copy(resource.toFile().toPath(), destResourceAbsolute.toFile().toPath());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					Files.delete(resource.toFile().toPath());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				//System.out.println("unit " + unit.getResource().getName() + ": no.");
			}
		}

		private boolean hasZeroUiImports(IImportDeclaration[] imports) {
			for (int i = 0; i < imports.length; i++) {
				if (isUiImport(imports[i])) {
					return false;
				}
			}
			return true;
		}

		private boolean isUiImport(IImportDeclaration iImportDeclaration) {
			String imported = iImportDeclaration.getElementName();
			if(imported.contains("*")) {
				return true;
			}
			String loc = typeFqcnToJar.get(imported);
			if( Arrays.asList(FORBIDDEN).contains(loc)) {
				return true;
			}
			return false;
		}

	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		new MyJob("Testing").schedule();
		return null;
	}
}
