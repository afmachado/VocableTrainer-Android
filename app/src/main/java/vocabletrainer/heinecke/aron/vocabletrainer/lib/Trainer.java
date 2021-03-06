package vocabletrainer.heinecke.aron.vocabletrainer.lib;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.VEntry;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.VList;
import vocabletrainer.heinecke.aron.vocabletrainer.lib.Storage.TrainerSettings;

/**
 * Trainer class
 */
public class Trainer {
    private final static String TAG = "Trainer";
    private Random rng;
    private AB_MODE order;
    private List<VList> lists;
    private List<VList> unsolvedLists;
    private VEntry cVocable = null;
    private int tips;
    private int total;
    private int unsolved;
    private int failed;
    private int timesToSolve;
    private int timesShowedSolution;
    private boolean showedSolution;
    private Database db;
    private TrainerSettings settings;
    private SessionStorageManager ssm;
    private boolean firstTimeVocLoad;

    /**
     * Creates a new Trainer
     *
     * @param lists     Vocable lists to use
     * @param settings   Trainer settings storage
     * @param newSession whether this is a new or a continued session
     */
    public Trainer(final ArrayList<VList> lists, final TrainerSettings settings, final Context context, final boolean newSession,
                   final SessionStorageManager ssm) {
        if (lists == null || lists.size() == 0 || settings == null)
            throw new IllegalArgumentException();
        this.settings = settings;
        this.tips = settings.tipsGiven;
        this.failed = settings.timesFailed;
        this.lists = lists;
        this.timesToSolve = settings.timesToSolve;
        this.unsolvedLists = new ArrayList<>();
        this.ssm = ssm;
        this.timesShowedSolution = 0;//todo: load from params
        db = new Database(context);
        rng = new Random();

        if (newSession) {
            wipeSession();
        }

        firstTimeVocLoad = settings.questioning != null; // load voc from settings.questioning
        getTableData();
        prepareData();
        this.getNext();
    }

    /**
     * Save vocable state<br>
     *  saves last vocable in question for later continue
     */
    public void saveVocState(){
        if(!ssm.saveLastVoc(cVocable)){
            Log.w(TAG,"unable to save vocable");
        }
    }

    /**
     * Wipe DB from (previous) session
     *
     * @return true on success
     */
    private boolean wipeSession() {
        return db.wipeSessionPoints();
    }

    /**
     * Calculate totals etc
     */
    private void prepareData() {
        total = 0;
        unsolved = 0;
        for (VList tbl : lists) {
            total += tbl.getTotalVocs();
            unsolved += tbl.getUnfinishedVocs();
        }
    }

    /**
     * Retreive information from DB
     */
    private void getTableData() {
        db.getSessionTableData(lists, unsolvedLists, settings);
    }

    /**
     * Returns the solution of the current vocable
     *
     * @return
     */
    public String getSolution() {
        Log.d(TAG,"getSolution");
        timesShowedSolution++;
        return getSolutionUnchecked();
    }

    /**
     * Returns the solution to the current vocable<br>
     *     Does not count it as failed.
     *
     *     <br><br>
     *         not to be confused with getSolutionUnchecked
     * @return Solution
     */
    public String getSolutionUncounted(){
        Log.d(TAG,"getSolutionUncounted");
        if (this.cVocable == null) {
            Log.e(TAG, "Null vocable!");
            return "";
        }
        showedSolution = true;
        return getSolutionUnchecked();
    }

    /**
     * Returns the solution<br>
     * No null checks are done or failed counter changes are made
     *
     * @return Solution
     */
    private String getSolutionUnchecked() {
        if (order == AB_MODE.A)
            return cVocable.getAWord();
        else
            return cVocable.getBWord();
    }

    /**
     * Check two string for equality, taking case sensitive settings into account
     * @param a
     * @param b
     * @return true if they are equals according to this trainings settings
     */
    private boolean equals(final String a, final String b){
        if(this.settings.caseSensitive){
            return a.equals(b);
        }else{
            return a.equalsIgnoreCase(b);
        }
    }

    /**
     * Checks for correct solution <br>
     *     Retrieves next vocable if correct
     *
     * @param tSolution
     * @return true on tSolution is correct
     */
    public boolean checkSolution(final String tSolution) {
        Log.d(TAG,"checkSolution");
        boolean bSolved = equals(getSolutionUnchecked(),tSolution);
        if(bSolved) {
            accountVocable(bSolved);
        }else {
            this.failed++;
        }
        return bSolved;
    }

    /** Accounts vocable as passed based on the parameter.<br>
     *     This function is called from external when checkSolution does not apply.
     * @param correct true when the vocable was answered correct
     */
    private void accountVocable(final boolean correct){
        Log.d(TAG,"accountVocable(correct:"+correct+")");
        if (this.cVocable == null) {
            Log.e(TAG, "Null vocable!");
            return;
        }
        if (correct)
            this.cVocable.setPoints(this.cVocable.getPoints() + 1);
        if (cVocable.getPoints() >= timesToSolve) {
            VList tbl = cVocable.getList();
            tbl.setUnfinishedVocs(tbl.getUnfinishedVocs() - 1);
            if (tbl.getUnfinishedVocs() <= 0) {
                unsolvedLists.remove(tbl);

                if (unsolvedLists.size() == 0) {
                    db.deleteSession();
                    Log.d(TAG, "finished");
                }
            }
        }
        Log.d(TAG,"getting next");
        getNext();
    }

    /**
     * function for modes where checkSolution doesn't apply<br>
     *     reads next vocabe & accounts passed=false as solution showed & failed
     * @param passed
     */
    public void updateVocable(final boolean passed){
        if(!passed){
            this.failed++;
            timesShowedSolution++;
        }
        accountVocable(passed);
    }

    /**
     * Returns true when all vocables are solved as many times as expected
     *
     * @return
     */
    public boolean isFinished() {
        return unsolvedLists.size() == 0;
    }

    /**
     * Update cVocable points
     *
     * @return true on success
     */
    private boolean updateVocable() {
        return db.updateEntryProgress(cVocable);
    }

    /**
     * Get next vocable
     */
    private void getNext() {
        if (cVocable != null) {
            if (!updateVocable()) {
                Log.e(TAG, "unable to update vocable!");
                return;
            }
        }
        showedSolution = false;

        if (unsolvedLists.size() == 0) {
            Log.d(TAG, "no unsolved lists remaining!");
        } else {
            int selected = rng.nextInt(unsolvedLists.size());
            VList tbl = unsolvedLists.get(selected);

            boolean allowRepetition = false;
            if (cVocable != null) {
                if (allowRepetition = unsolvedLists.size() == 1 && unsolvedLists.get(0).getUnfinishedVocs() == 1) {
                    Log.d(TAG, "one left");
                } else if (tbl.getUnfinishedVocs() == 1 && tbl.getId() == cVocable.getList().getId()) {
                    // handle table has only one entry left
                    // prevent repeating last vocable of table
                    if (selected + 1 >= unsolvedLists.size())
                        selected--;
                    else
                        selected++;
                    Log.d(TAG, "selectionID:" + selected + " max(-1):" + unsolvedLists.size());
                    tbl = unsolvedLists.get(selected);
                }
            }

            if(firstTimeVocLoad){
                firstTimeVocLoad = false;
                cVocable = settings.questioning;
            }else {
                cVocable = db.getRandomTrainerEntry(tbl, cVocable, settings, allowRepetition);
            }
            if (cVocable == null) {
                Log.e(TAG, "New vocable is null!");
            }
            boolean mode = settings.mode == TEST_MODE.A;
            if (settings.mode == TEST_MODE.RANDOM) {
                mode = rng.nextBoolean();
            }
            if (mode) {
                order = AB_MODE.A;
            } else {
                order = AB_MODE.B;
            }
        }
    }

    /**
     * Returns the non-solution column of the vocable<br>
     * returns an empty string when there is no current vocable
     *
     * @return
     */
    public String getQuestion() {
        if (cVocable == null)
            return "";

        return order == AB_MODE.A ? cVocable.getBWord() : cVocable.getAWord();
    }

    /**
     * Returns the tip, increasing the counter<br>
     * returns an empty string when there is no current vocable
     *
     * @return
     */
    public String getTip() {
        if (this.cVocable == null)
            return "";

        this.tips++;
        return cVocable.getTip();
    }

    /**
     * Returns the column name of the question<br>
     * returns an empty string when there is no current vocable
     *
     * @return
     */
    public String getColumnNameExercise() {
        if (this.cVocable == null)
            return "";

        return order == AB_MODE.A ? cVocable.getList().getNameB() : cVocable.getList().getNameA();
    }

    /**
     * Returns the column name of the solution<br>
     * returns an empty string when there is no current vocable
     *
     * @return
     */
    public String getColumnNameSolution() {
        if (this.cVocable == null)
            return "";

        return order == AB_MODE.B ? cVocable.getList().getNameB() : cVocable.getList().getNameA();
    }

    /**
     * @return remaining vocables
     */
    public int remaining() {
        return unsolved;
    }

    /**
     * @return solved vocables
     */
    public int solved() {
        return total - unsolved;
    }

    /**
     * Testing mode for trainer<br>
     *     Ask column a/b/a&b random
     */
    public enum TEST_MODE {
        A(0), B(1), RANDOM(2);
        private final int id;

        TEST_MODE(int id) {
            this.id = id;
        }

        public static TEST_MODE fromInt(int code) {
            for (TEST_MODE typе : TEST_MODE.values()) {
                if (typе.getValue() == code) {
                    return typе;
                }
            }
            return null;
        }

        public int getValue() {
            return id;
        }
    }

    /**
     * Enum to store the current column that is displayed
     */
    private enum AB_MODE {A, B}
}
